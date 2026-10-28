# Manual Live Gemini Assistant Testing Guide

This guide describes how an administrator or developer can manually verify live Google Gemini LLM integration in FitFlow.

> [!IMPORTANT]
> **Zero Secrets Policy**: Never commit, log, or hardcode any Gemini API key in configuration files, test scripts, or git commits. The API key is stored encrypted at rest via AES-256-GCM in the MySQL `app_secrets` table.

---

## 1. Prerequisites

1. MySQL, PostgreSQL, and RabbitMQ native services are running:
   - MySQL on `localhost:3306` (`fitflow` database)
   - PostgreSQL on `localhost:5432` (`fitflow_agent` database)
   - RabbitMQ on `localhost:5672`
2. FitFlow backend application is running:
   ```powershell
   mvn spring-boot:run
   ```
3. An active administrator user account and an active member account exist.

---

## 2. Setting the Gemini API Key at Runtime

Admin runtime settings allow entering and rotating the Gemini API key without application restarts:

### Step 2.1: Authenticate as Admin
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "admin@fitflow.com",
  "password": "AdminPassword123!"
}
```
Extract the `accessToken` from the response.

### Step 2.2: Store and Encrypt the Gemini Key
```bash
PUT /api/v1/admin/settings/ai
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json

{
  "apiKey": "<YOUR_GEMINI_API_KEY>",
  "modelName": "gemini-1.5-flash"
}
```
The key is immediately encrypted with AES-256-GCM and persisted in `app_secrets`.

### Step 2.3: Verify Connection
```bash
POST /api/v1/admin/settings/ai/test
Authorization: Bearer <ADMIN_TOKEN>
```
Expected response:
```json
{
  "status": "UP",
  "provider": "GEMINI",
  "modelName": "gemini-1.5-flash",
  "latencyMs": 350
}
```

### Step 2.4: Check Agent Status
```bash
GET /api/v1/agent/status
Authorization: Bearer <MEMBER_OR_ADMIN_TOKEN>
```
Expected response:
```json
{
  "mode": "GEMINI",
  "circuitBreakerStatus": "CLOSED",
  "agentsAvailable": [
    "BookingAgent",
    "MembershipAgent",
    "IntakeAgent",
    "SafeClassMatcherAgent"
  ]
}
```

---

## 3. Sample Member Interactive Prompts

Log in as a test member (`POST /api/v1/auth/login`) and obtain a member token.

### Prompt 1: Safe Class Discovery & Medical Guidance Check
- **Endpoint**: `POST /api/v1/agent/chat`
- **Request Body**:
  ```json
  {
    "message": "I have knee pain and moderate asthma. Which fitness classes are safe for me this week?"
  }
  ```
- **Expected Behavior**:
  - `SupervisorRouter` routes to `SafeClassMatcherAgent`.
  - Agent invokes `get_safe_classes` tool.
  - NumberGuard (G1) verifies all dates, capacities, and session counts.
  - Response lists safe sessions (e.g. Gentle Flow Yoga) with safety modifications.
  - Response strictly contains disclaimer: *"Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use."*

### Prompt 2: Booking Proposal Flow (Human-in-the-Loop)
- **Endpoint**: `POST /api/v1/agent/chat`
- **Request Body**:
  ```json
  {
    "conversationId": 1,
    "message": "Can you book me into tomorrow morning's Yoga class?"
  }
  ```
- **Expected Behavior**:
  - `SupervisorRouter` routes to `BookingAgent`.
  - Agent invokes `search_sessions` and `propose_booking`.
  - Agent returns a proposal summary and instructs user: *"I've created a booking proposal for Agent Flow Yoga. Please review the details and click Confirm on your screen."*
  - No database booking record is finalized yet (only `pending_actions` row with status `PENDING`).
- **Confirmation Step**:
  ```bash
  POST /api/v1/agent/actions/{actionId}/confirm
  Authorization: Bearer <MEMBER_TOKEN>
  ```
  - Result status transitions to `CONFIRMED`.
  - Booking is created in MySQL with idempotency key `agent-action-{actionId}`.

### Prompt 3: Plan Comparison & Pricing
- **Endpoint**: `POST /api/v1/agent/chat`
- **Request Body**:
  ```json
  {
    "message": "What membership plans are available and how much do they cost?"
  }
  ```
- **Expected Behavior**:
  - `SupervisorRouter` routes to `MembershipAgent`.
  - Agent invokes `list_plans`.
  - Output Hygiene Guard (G6) and NumberGuard (G1) verify prices against tool output (e.g., ₹899 for Pro, ₹499 for Basic).
  - Agent explains plan differences without inventing numerical limits or prices.

---

## 4. Resetting to Mock Mode After Testing

To reset the system back to hermetic mock mode (useful for testing environments):
```bash
DELETE /api/v1/admin/settings/ai/key
Authorization: Bearer <ADMIN_TOKEN>
```
`GET /api/v1/agent/status` will report `"mode": "MOCK"`.
