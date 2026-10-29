# FitFlow Payments Runbook (Razorpay Integration)

This runbook documents operational procedures for configuring, operating, rotating, and troubleshooting the Razorpay payment gateway integration for FitFlow.

---

## 1. Razorpay Dashboard Initial Setup

### 1.1 Generating API Keys
1. Log in to the [Razorpay Dashboard](https://dashboard.razorpay.com).
2. Ensure you are in **Test Mode** (toggle on the top nav) during development and testing.
3. Navigate to **Account & Settings** -> **API Keys**.
4. Click **Generate Key**.
5. Save the generated `Key ID` and `Key Secret` immediately. Razorpay will only show the `Key Secret` once.
   - Key ID formats:
     - Test mode: `rzp_test_...` (matches `^rzp_test_[A-Za-z0-9]{8,40}$`)
     - Live mode: `rzp_live_...` (matches `^rzp_live_[A-Za-z0-9]{8,40}$`)
   - Key Secret: 16 to 80 characters, no whitespace.

### 1.2 Webhook Configuration
1. In the Razorpay Dashboard, go to **Account & Settings** -> **Webhooks**.
2. Click **Add New Webhook**.
3. Set **Webhook URL**:
   - For local development with a tunnel: `https://<tunnel-domain>/api/v1/payments/webhooks/razorpay`
   - For production/staging: `https://<domain>/api/v1/payments/webhooks/razorpay`
4. Set **Secret**: Enter a secure random string (e.g. 32-character hexadecimal or base64 token).
5. Set **Alert Email**: Enter team on-call email address.
6. Select the **Active Events**:
   - `payment.captured`
   - `payment.failed`
   - `order.paid`
   - `refund.processed`
   - `refund.failed`
7. Click **Create Webhook**. Save the secret for runtime admin configuration.

---

## 2. FitFlow Runtime Configuration

FitFlow never reads payment secrets from properties or environment files. Keys and secrets are entered via administrative APIs at runtime, encrypted at rest using AES-256-GCM in the `app_secrets` database table.

### 2.1 Checking Current Payment Configuration
```http
GET /api/v1/admin/settings/payments
Authorization: Bearer <ADMIN_OR_SUPER_ADMIN_JWT>
```
Response:
```json
{
  "gateway": "MOCK",
  "razorpay": {
    "configured": false,
    "keyId": null,
    "keySecretMaskedLast4": null,
    "webhookSecretConfigured": false,
    "mode": null
  },
  "webhookUrlHint": "/api/v1/payments/webhooks/razorpay"
}
```

### 2.2 Entering Razorpay Keys
```http
PUT /api/v1/admin/settings/payments/razorpay
Authorization: Bearer <ADMIN_OR_SUPER_ADMIN_JWT>
Content-Type: application/json

{
  "keyId": "rzp_test_ScYoa7HPECrM5A",
  "keySecret": "wqgMHBfXKilwkHhZHuutoB7v",
  "webhookSecret": "your_webhook_secret_here"
}
```

### 2.3 Testing Credentials Before Switching
Before activating Razorpay, a non-mutating test connection must be verified. FitFlow requires a successful `/test` call within the last 10 minutes before allowing a switch:
```http
POST /api/v1/admin/settings/payments/razorpay/test
Authorization: Bearer <ADMIN_OR_SUPER_ADMIN_JWT>
```
Response:
```json
{
  "ok": true,
  "httpStatus": 200,
  "message": "Connection successful"
}
```

### 2.4 Switching the Active Gateway
```http
PUT /api/v1/admin/settings/payments/gateway
Authorization: Bearer <ADMIN_OR_SUPER_ADMIN_JWT>
Content-Type: application/json

{
  "gateway": "RAZORPAY"
}
```

### 2.5 Deleting Credentials & Safe Fallback
To remove credentials or immediately fallback to `MOCK`:
```http
DELETE /api/v1/admin/settings/payments/razorpay
Authorization: Bearer <ADMIN_OR_SUPER_ADMIN_JWT>
```
This removes the encrypted secrets and resets `payments.gateway` to `MOCK`.

---

## 3. Switching from Test to Live (Production Checklist)

To prevent accidental charges, FitFlow enforces strict live key guards:

1. **Active Profile**: The Spring Boot active profile must include `prod`. In non-prod environments (`dev`, `test`), any key starting with `rzp_live_` is immediately rejected with HTTP 400.
2. **SUPER_ADMIN Permission Flag**: A `SUPER_ADMIN` must enable live mode in `app_settings`:
   - Setting name: `payments.allow-live`
   - Setting value: `true`
3. **Configure Live Keys**:
   Execute `PUT /api/v1/admin/settings/payments/razorpay` with `rzp_live_...` credentials.
4. **Run Live Test**:
   Execute `POST /api/v1/admin/settings/payments/razorpay/test`. Confirm `ok=true` and HTTP 200.
5. **Switch Gateway**:
   Execute `PUT /api/v1/admin/settings/payments/gateway` with `{"gateway": "RAZORPAY"}` within 10 minutes of the successful test.
6. **Verify Production Webhook**:
   Verify in Razorpay Dashboard that the webhook URL points to the production HTTPS endpoint and test with a simulated event.

---

## 4. Cryptographic Signature Verification

Razorpay authenticates callbacks and checkout submissions via HMAC-SHA256:

### 4.1 Standard Checkout Signature
- **Payload**: `order_id + "|" + razorpay_payment_id`
- **Algorithm**: `HMAC_SHA256(keySecret, payload)`
- **Comparison**: Constant-time equality (`MessageDigest.isEqual`) on UTF-8 bytes to prevent timing attacks.
- **Verification Rule**: Never trust the client alone. FitFlow performs a server-side fetch (`GET /v1/payments/{paymentId}`) to verify `order_id`, `amount`, and `currency == "INR"` match the database records.

### 4.2 Webhook Signature
- **Header**: `X-Razorpay-Signature`
- **Event ID**: `X-Razorpay-Event-Id`
- **Payload**: Raw request body bytes.
- **Algorithm**: `HMAC_SHA256(webhookSecret, rawBodyBytes)`
- **Deduplication**: `X-Razorpay-Event-Id` is recorded in `payment_webhook_events`. If an event ID has already been processed, FitFlow responds with HTTP 200 `{"status": "duplicate"}` and skips reprocessing.

---

## 5. Key Rotation Procedure

To rotate credentials without downtime:
1. Generate the new key pair in Razorpay Dashboard (Razorpay supports holding 2 active keys during rotation).
2. Call `POST /api/v1/admin/settings/payments/razorpay/test` with the new keys.
3. Update keys via `PUT /api/v1/admin/settings/payments/razorpay`. Existing payments retain their original gateway order/payment references; future payments will use the new keys.
4. Deactivate the retired key in Razorpay Dashboard.

---

## 6. Troubleshooting & Operational Runbooks

### 6.1 Stuck PENDING Payments (PaymentReconcileJob)
- **Schedule**: `PaymentReconcileJob` runs automatically every 5 minutes.
- **15m - 60m Pending**:
  - The job queries Razorpay `GET /v1/orders/{gatewayOrderId}/payments`.
  - If a captured payment exists, the payment is finalized as `SUCCEEDED`.
  - If an authorized payment exists, capture is executed.
- **> 60m Pending**:
  - If no payment was captured or authorized, the payment is marked `FAILED` with failure reason `reconcile_abandoned`.

### 6.2 Stuck PENDING Refunds
- **Threshold**: Refunds in `PENDING` status older than 10 minutes are polled using `GET /v1/payments/{paymentId}/refunds/{refundId}`.
- If status is `processed`, the local refund is updated to `PROCESSED`.
- If status is `failed`, the refund is updated to `FAILED`.

### 6.3 Webhook Failures or Missed Events
- Inspect `payment_webhook_events` table:
  ```sql
  SELECT id, event_type, status, error_message, created_at 
  FROM payment_webhook_events 
  ORDER BY created_at DESC LIMIT 50;
  ```
- Razorpay retries failed webhooks automatically over a 24-hour exponential backoff schedule.
- Webhook endpoints always return HTTP 200 for idempotent duplicates or unrecognized event types to prevent webhook spamming.
