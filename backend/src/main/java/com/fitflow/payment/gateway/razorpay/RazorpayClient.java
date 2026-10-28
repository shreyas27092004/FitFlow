package com.fitflow.payment.gateway.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayCaptureRequest;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayOrderPaymentsResponse;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayOrderRequest;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayOrderResponse;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayPaymentResponse;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayRefundRequest;
import com.fitflow.payment.gateway.razorpay.dto.RazorpayRefundResponse;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.payment.settings.dto.RazorpayTestResponse;
import com.fitflow.settings.repository.AppSettingRepository;
import com.fitflow.settings.SecretsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Component
@Slf4j
public class RazorpayClient {

    private final String baseUrl;
    private final AppSettingRepository appSettingRepository;
    private final SecretsService secretsService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;

    public RazorpayClient(
            @Value("${fitflow.payments.razorpay.base-url:https://api.razorpay.com}") String baseUrl,
            AppSettingRepository appSettingRepository,
            SecretsService secretsService,
            ObjectMapper objectMapper,
            java.time.Clock clock
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.appSettingRepository = appSettingRepository;
        this.secretsService = secretsService;
        this.objectMapper = objectMapper;
        this.clock = clock;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(this.baseUrl)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Optional<String> getKeyId() {
        return appSettingRepository.findById("razorpay.key_id")
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank());
    }

    public Optional<String> getKeySecret() {
        return secretsService.get("razorpay_key_secret");
    }

    public Optional<String> getWebhookSecret() {
        return secretsService.get("razorpay_webhook_secret");
    }

    public boolean isConfigured() {
        return getKeyId().isPresent() && getKeySecret().isPresent();
    }

    public Optional<String> getMaskedKeySecretLast4() {
        return secretsService.masked("razorpay_key_secret");
    }

    public boolean isWebhookSecretConfigured() {
        return secretsService.exists("razorpay_webhook_secret");
    }

    public void setCredentials(String keyId, String keySecret, String webhookSecret, Long actorId) {
        AppSetting setting = appSettingRepository.findById("razorpay.key_id")
                .orElseGet(() -> AppSetting.builder().name("razorpay.key_id").build());
        setting.setValue(keyId);
        appSettingRepository.save(setting);

        secretsService.set("razorpay_key_secret", keySecret, actorId);
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            secretsService.set("razorpay_webhook_secret", webhookSecret.trim(), actorId);
        }
    }

    public void deleteCredentials() {
        appSettingRepository.deleteById("razorpay.key_id");
        secretsService.delete("razorpay_key_secret");
        secretsService.delete("razorpay_webhook_secret");
        appSettingRepository.deleteById("razorpay.last_test_success_at");
    }

    public void recordLastTestSuccess(java.time.Instant instant) {
        AppSetting setting = appSettingRepository.findById("razorpay.last_test_success_at")
                .orElseGet(() -> AppSetting.builder().name("razorpay.last_test_success_at").build());
        setting.setValue(instant.toString());
        appSettingRepository.save(setting);
    }

    public Optional<java.time.Instant> getLastTestSuccessAt() {
        return appSettingRepository.findById("razorpay.last_test_success_at")
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .flatMap(v -> {
                    try {
                        return Optional.of(java.time.Instant.parse(v));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
    }

    public RazorpayTestResponse testConnection() {
        Optional<String> keyIdOpt = getKeyId();
        Optional<String> secretOpt = getKeySecret();

        if (keyIdOpt.isEmpty() || secretOpt.isEmpty()) {
            return new RazorpayTestResponse(false, 400, "Razorpay keys are not configured");
        }

        String authHeader = buildBasicAuthHeader(keyIdOpt.get(), secretOpt.get());

        try {
            RazorpayTestResponse response = restClient.get()
                    .uri("/v1/payments?count=1")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 200) {
                            return new RazorpayTestResponse(true, 200, "Connection successful");
                        } else if (code == 401) {
                            return new RazorpayTestResponse(false, 401, "Invalid key id or secret");
                        } else {
                            return new RazorpayTestResponse(false, code, "Razorpay responded with status " + code);
                        }
                    });

            if (response.ok()) {
                recordLastTestSuccess(clock.instant());
            }
            return response;
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401) {
                return new RazorpayTestResponse(false, 401, "Invalid key id or secret");
            }
            return new RazorpayTestResponse(false, code, "Razorpay responded with status " + code);
        } catch (Exception e) {
            log.error("Razorpay test connection error: {}", e.getMessage());
            return new RazorpayTestResponse(false, 500, "Failed to connect to Razorpay: " + e.getMessage());
        }
    }

    public RazorpayOrderResponse createOrder(RazorpayOrderRequest request) {
        String authHeader = getRequiredAuthHeader();

        try {
            return restClient.post()
                    .uri("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RazorpayOrderResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Razorpay createOrder failed with status {}", e.getStatusCode().value());
            throw handleHttpError("create order", e);
        } catch (Exception e) {
            log.error("Razorpay createOrder network/timeout error: {}", e.getMessage());
            throw ApiException.internalError("Payment gateway connection error");
        }
    }

    public RazorpayPaymentResponse fetchPayment(String paymentId) {
        String authHeader = getRequiredAuthHeader();

        try {
            return restClient.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RazorpayPaymentResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Razorpay fetchPayment id={} failed with status {}", paymentId, e.getStatusCode().value());
            throw handleHttpError("fetch payment", e);
        } catch (Exception e) {
            log.error("Razorpay fetchPayment id={} network error: {}", paymentId, e.getMessage());
            throw ApiException.internalError("Payment gateway connection error");
        }
    }

    public RazorpayPaymentResponse capturePayment(String paymentId, long amountPaise, String currency) {
        String authHeader = getRequiredAuthHeader();
        RazorpayCaptureRequest captureReq = new RazorpayCaptureRequest(amountPaise, currency);

        try {
            return restClient.post()
                    .uri("/v1/payments/{id}/capture", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(captureReq)
                    .retrieve()
                    .body(RazorpayPaymentResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Razorpay capturePayment id={} failed with status {}", paymentId, e.getStatusCode().value());
            throw handleHttpError("capture payment", e);
        } catch (Exception e) {
            log.error("Razorpay capturePayment id={} network error: {}", paymentId, e.getMessage());
            throw ApiException.internalError("Payment gateway connection error");
        }
    }

    public RazorpayRefundResponse createRefund(String paymentId, RazorpayRefundRequest request) {
        String authHeader = getRequiredAuthHeader();

        try {
            return restClient.post()
                    .uri("/v1/payments/{id}/refund", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RazorpayRefundResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Razorpay createRefund for paymentId={} failed with status {}", paymentId, e.getStatusCode().value());
            throw handleHttpError("create refund", e);
        } catch (Exception e) {
            log.error("Razorpay createRefund for paymentId={} network error: {}", paymentId, e.getMessage());
            throw ApiException.internalError("Payment gateway connection error");
        }
    }

    public RazorpayRefundResponse fetchRefund(String refundId) {
        String authHeader = getRequiredAuthHeader();

        try {
            return restClient.get()
                    .uri("/v1/refunds/{id}", refundId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RazorpayRefundResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Razorpay fetchRefund id={} failed with status {}", refundId, e.getStatusCode().value());
            throw handleHttpError("fetch refund", e);
        } catch (Exception e) {
            log.error("Razorpay fetchRefund id={} network error: {}", refundId, e.getMessage());
            throw ApiException.internalError("Payment gateway connection error");
        }
    }

    public RazorpayOrderPaymentsResponse fetchOrderPayments(String orderId) {
        String authHeader = getRequiredAuthHeader();

        try {
            return restClient.get()
                    .uri("/v1/orders/{orderId}/payments", orderId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RazorpayOrderPaymentsResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Razorpay fetchOrderPayments orderId={} failed with status {}", orderId, e.getStatusCode().value());
            throw handleHttpError("fetch order payments", e);
        } catch (Exception e) {
            log.error("Razorpay fetchOrderPayments orderId={} network error: {}", orderId, e.getMessage());
            throw ApiException.internalError("Payment gateway connection error");
        }
    }

    private String getRequiredAuthHeader() {
        String keyId = getKeyId().orElseThrow(() -> ApiException.badRequest("Razorpay keyId is not configured"));
        String secret = getKeySecret().orElseThrow(() -> ApiException.badRequest("Razorpay keySecret is not configured"));
        return buildBasicAuthHeader(keyId, secret);
    }

    private String buildBasicAuthHeader(String keyId, String keySecret) {
        String auth = keyId + ":" + keySecret;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    private RuntimeException handleHttpError(String action, RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        if (status.is4xxClientError()) {
            String safeMsg = "Payment gateway request failed (" + action + ")";
            try {
                var map = objectMapper.readValue(e.getResponseBodyAsString(), java.util.Map.class);
                if (map != null && map.get("error") instanceof java.util.Map<?, ?> errMap) {
                    Object desc = errMap.get("description");
                    if (desc != null && !desc.toString().isBlank()) {
                        safeMsg = desc.toString();
                    }
                }
            } catch (Exception ignored) {}
            return ApiException.paymentFailed(safeMsg);
        }
        return ApiException.internalError("Payment gateway error during " + action);
    }
}
