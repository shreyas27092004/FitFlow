package com.fitflow.devtools;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!prod")
@Tag(name = "Dev Tools", description = "Development only test tools")
public class DevRazorpayCheckoutController {

    @GetMapping(value = "/dev/razorpay-checkout", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Dev-only standalone Razorpay test checkout page")
    public ResponseEntity<String> getCheckoutPage(
            @RequestParam(name = "paymentId", required = false) Long paymentId,
            @RequestParam(name = "token", required = false) String token
    ) {
        String defaultPaymentId = paymentId != null ? String.valueOf(paymentId) : "";
        String defaultToken = token != null ? token : "";

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>FitFlow Razorpay Dev Checkout</title>
                    <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f4f6f8; margin: 0; padding: 24px; color: #202124; }
                        .card { max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
                        h2 { margin-top: 0; color: #1976d2; }
                        label { display: block; font-weight: 600; margin-top: 14px; margin-bottom: 4px; font-size: 14px; }
                        input, select { width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid #ccc; border-radius: 4px; font-size: 14px; }
                        button { background: #1976d2; color: #fff; border: none; border-radius: 4px; padding: 12px 20px; font-size: 15px; font-weight: 600; cursor: pointer; margin-top: 18px; width: 100%; }
                        button:hover { background: #1565c0; }
                        .result { margin-top: 20px; padding: 16px; border-radius: 6px; font-size: 14px; display: none; word-break: break-all; white-space: pre-wrap; }
                        .success { background: #e8f5e9; color: #2e7d32; border: 1px solid #a5d6a7; }
                        .error { background: #ffebee; color: #c62828; border: 1px solid #ef9a9a; }
                        .section { border-top: 1px solid #e0e0e0; margin-top: 20px; padding-top: 14px; }
                    </style>
                </head>
                <body>
                <div class="card">
                    <h2>FitFlow &bull; Dev Razorpay Checkout</h2>
                    <p style="font-size: 13px; color: #666;">Test checkout helper for validating Razorpay sandbox keys.</p>

                    <label for="token">Member JWT Token (Bearer)</label>
                    <input type="text" id="token" value="{{TOKEN}}" placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...">

                    <label for="paymentId">Payment ID</label>
                    <input type="number" id="paymentId" value="{{PAYMENT_ID}}" placeholder="e.g. 42">

                    <button id="checkoutBtn" onclick="startExistingCheckout()">Fetch Payload & Pay with Razorpay</button>

                    <div class="section">
                        <label for="planCode">Or subscribe to a new plan:</label>
                        <select id="planCode">
                            <option value="BASIC">BASIC (49900 paise / 30 days / 8 sessions)</option>
                            <option value="PRO">PRO (89900 paise / 30 days / 16 sessions)</option>
                            <option value="ELITE">ELITE (149900 paise / 30 days / Unlimited)</option>
                        </select>
                        <button style="background: #2e7d32;" onclick="startSubscribeAndCheckout()">Subscribe & Pay</button>
                    </div>

                    <div id="resultBox" class="result"></div>
                </div>

                <script>
                    function showResult(success, message) {
                        const box = document.getElementById('resultBox');
                        box.style.display = 'block';
                        box.className = 'result ' + (success ? 'success' : 'error');
                        box.textContent = (success ? 'STATUS: SUCCEEDED\\n\\n' : 'STATUS: FAILED\\n\\n') + message;
                    }

                    async function startExistingCheckout() {
                        const token = document.getElementById('token').value.trim();
                        const paymentId = document.getElementById('paymentId').value.trim();

                        if (!token) { alert('Please enter JWT bearer token'); return; }
                        if (!paymentId) { alert('Please enter Payment ID'); return; }

                        try {
                            const resp = await fetch('/api/v1/payments/' + paymentId + '/client-payload', {
                                headers: { 'Authorization': 'Bearer ' + token }
                            });
                            if (!resp.ok) {
                                const err = await resp.text();
                                showResult(false, 'Failed to fetch client payload: ' + resp.status + ' ' + err);
                                return;
                            }
                            const payload = await resp.json();
                            openRazorpay(paymentId, token, payload);
                        } catch (err) {
                            showResult(false, 'Network error: ' + err.message);
                        }
                    }

                    async function startSubscribeAndCheckout() {
                        const token = document.getElementById('token').value.trim();
                        const planCode = document.getElementById('planCode').value;

                        if (!token) { alert('Please enter JWT bearer token'); return; }

                        try {
                            const idempotencyKey = 'dev-sub-' + Date.now();
                            const resp = await fetch('/api/v1/memberships/subscribe', {
                                method: 'POST',
                                headers: {
                                    'Authorization': 'Bearer ' + token,
                                    'Content-Type': 'application/json',
                                    'Idempotency-Key': idempotencyKey
                                },
                                body: JSON.stringify({ planCode: planCode })
                            });

                            if (!resp.ok) {
                                const err = await resp.text();
                                showResult(false, 'Subscribe failed: ' + resp.status + ' ' + err);
                                return;
                            }

                            const result = await resp.json();
                            if (result.payment && result.payment.paymentId) {
                                const paymentId = result.payment.paymentId;
                                document.getElementById('paymentId').value = paymentId;
                                openRazorpay(paymentId, token, result.payment.clientPayload);
                            } else {
                                showResult(true, 'Membership applied directly without payment:\\n' + JSON.stringify(result, null, 2));
                            }
                        } catch (err) {
                            showResult(false, 'Network error: ' + err.message);
                        }
                    }

                    function openRazorpay(paymentId, token, payload) {
                        const options = {
                            key: payload.keyId,
                            amount: payload.amount,
                            currency: payload.currency || 'INR',
                            name: payload.name || 'FitFlow',
                            description: payload.description || 'FitFlow Membership',
                            order_id: payload.orderId,
                            prefill: payload.prefill || {},
                            theme: payload.theme || { color: '#1976d2' },
                            handler: async function (response) {
                                try {
                                    const confirmResp = await fetch('/api/v1/payments/' + paymentId + '/confirm', {
                                        method: 'POST',
                                        headers: {
                                            'Authorization': 'Bearer ' + token,
                                            'Content-Type': 'application/json'
                                        },
                                        body: JSON.stringify({
                                            clientPayload: {
                                                razorpay_order_id: response.razorpay_order_id,
                                                razorpay_payment_id: response.razorpay_payment_id,
                                                razorpay_signature: response.razorpay_signature
                                            }
                                        })
                                    });

                                    const confirmData = await confirmResp.json();
                                    if (confirmResp.ok && confirmData.status === 'SUCCEEDED') {
                                        showResult(true, JSON.stringify(confirmData, null, 2));
                                    } else {
                                        showResult(false, JSON.stringify(confirmData, null, 2));
                                    }
                                } catch (e) {
                                    showResult(false, 'Error during confirmation: ' + e.message);
                                }
                            },
                            modal: {
                                ondismiss: function () {
                                    showResult(false, 'Payment cancelled or closed by user.');
                                }
                            }
                        };

                        const rzp = new Razorpay(options);
                        rzp.open();
                    }
                </script>
                </body>
                </html>
                """
                .replace("{{TOKEN}}", defaultToken)
                .replace("{{PAYMENT_ID}}", defaultPaymentId);

        return ResponseEntity.ok(html);
    }
}
