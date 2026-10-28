package com.fitflow.payment.gateway;

import com.fitflow.common.error.ApiException;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GatewayRouter {

    private final Map<String, PaymentGateway> gateways;
    private final AppSettingRepository appSettingRepository;

    public GatewayRouter(List<PaymentGateway> gatewayList, AppSettingRepository appSettingRepository) {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(g -> g.name().toUpperCase(), Function.identity()));
        this.appSettingRepository = appSettingRepository;
    }

    public PaymentGateway getActiveGateway() {
        String activeName = appSettingRepository.findById("payments.gateway")
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(MockPaymentGateway.GATEWAY_NAME)
                .toUpperCase();

        PaymentGateway gateway = gateways.get(activeName);
        if (gateway == null) {
            log.warn("Configured gateway '{}' not found, falling back to MOCK", activeName);
            return gateways.getOrDefault(MockPaymentGateway.GATEWAY_NAME, gateways.values().iterator().next());
        }
        return gateway;
    }

    public PaymentGateway getGateway(String name) {
        if (name == null || name.isBlank()) {
            return getActiveGateway();
        }
        PaymentGateway gateway = gateways.get(name.toUpperCase());
        if (gateway == null) {
            throw ApiException.notFound("Payment gateway '" + name + "' not found");
        }
        return gateway;
    }
}
