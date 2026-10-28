package com.fitflow.membership.proration;

import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProrationService {

    private final Map<String, ProrationPolicy> policies;
    private final AppSettingRepository appSettingRepository;
    private final ProrationPolicy defaultPolicy;

    public ProrationService(List<ProrationPolicy> policyList, AppSettingRepository appSettingRepository) {
        this.policies = policyList.stream()
                .collect(Collectors.toMap(ProrationPolicy::name, Function.identity()));
        this.appSettingRepository = appSettingRepository;
        this.defaultPolicy = policies.getOrDefault(
                MinRatioProrationPolicy.POLICY_NAME,
                new MinRatioProrationPolicy()
        );
    }

    public ProrationResult calculate(ProrationInput input) {
        String policyName = appSettingRepository.findById("refund.policy")
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(MinRatioProrationPolicy.POLICY_NAME);

        ProrationPolicy policy = policies.getOrDefault(policyName, defaultPolicy);
        log.debug("Calculating proration using policy '{}'", policy.name());
        return policy.calculate(input);
    }
}
