package com.fitflow.membership.proration;

import com.fitflow.common.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MinRatioProrationPolicy implements ProrationPolicy {

    public static final String POLICY_NAME = "MIN_RATIO_V1";
    private static final int SCALE = 10;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public String name() {
        return POLICY_NAME;
    }

    @Override
    public ProrationResult calculate(ProrationInput input) {
        List<String> explanation = new ArrayList<>();

        // 1. Calculate daysRemaining: whole days from now to ends_at, rounded UP, min 0
        int daysRemaining = 0;
        if (input.now() != null && input.endsAt() != null) {
            Duration duration = Duration.between(input.now(), input.endsAt());
            long seconds = duration.getSeconds();
            if (seconds > 0) {
                long days = (seconds + 86400L - 1L) / 86400L;
                daysRemaining = (int) Math.min(days, input.totalDays());
            }
        }
        int totalDays = Math.max(1, input.totalDays());
        BigDecimal daysRatio = BigDecimal.valueOf(daysRemaining)
                .divide(BigDecimal.valueOf(totalDays), SCALE, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE)
                .max(BigDecimal.ZERO);

        explanation.add("Days remaining: %d of %d (ratio: %s)".formatted(
                daysRemaining, totalDays, daysRatio.stripTrailingZeros().toPlainString()));

        // 2. Calculate sessionsRatio
        Integer sessionsRemaining = null;
        BigDecimal sessionsRatio = null;
        BigDecimal refundableFraction;

        if (input.sessionsTotal() == null) {
            // Unlimited plan: ratio = daysRatio
            refundableFraction = daysRatio;
            explanation.add("Unlimited plan: sessions ratio omitted, refundable fraction = days ratio (%s)".formatted(
                    daysRatio.stripTrailingZeros().toPlainString()));
        } else {
            int total = Math.max(1, input.sessionsTotal());
            int remaining = Math.max(0, input.sessionsTotal() - input.sessionsUsed());
            sessionsRemaining = remaining;
            sessionsRatio = BigDecimal.valueOf(remaining)
                    .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE)
                    .max(BigDecimal.ZERO);

            refundableFraction = daysRatio.min(sessionsRatio);
            explanation.add("Sessions remaining: %d of %d (ratio: %s)".formatted(
                    remaining, total, sessionsRatio.stripTrailingZeros().toPlainString()));
            explanation.add("Policy %s: min(daysRatio, sessionsRatio) = min(%s, %s) = %s".formatted(
                    POLICY_NAME,
                    daysRatio.stripTrailingZeros().toPlainString(),
                    sessionsRatio.stripTrailingZeros().toPlainString(),
                    refundableFraction.stripTrailingZeros().toPlainString()));
        }

        // Clamp fraction to [0, 1]
        refundableFraction = refundableFraction.min(BigDecimal.ONE).max(BigDecimal.ZERO);

        // 3. Credit = round_half_up(amount_paid_paise * refundableFraction)
        BigDecimal exactCredit = BigDecimal.valueOf(input.amountPaidPaise()).multiply(refundableFraction);
        long creditPaise = Money.fromBigDecimalHalfUp(exactCredit).paise();
        explanation.add("Calculated credit: %d paise from %d paise paid".formatted(creditPaise, input.amountPaidPaise()));

        long amountDuePaise = 0L;
        long refundPaise = 0L;
        Long cappedByCashPaise = null;

        if (input.isCancel()) {
            // Cancellation workflow
            BigDecimal feePercent = input.feePercent() != null ? input.feePercent() : BigDecimal.ZERO;
            if (feePercent.compareTo(BigDecimal.ZERO) < 0) {
                feePercent = BigDecimal.ZERO;
            }
            BigDecimal feeFraction = feePercent.divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            long cancellationFee = Money.fromBigDecimalHalfUp(
                    BigDecimal.valueOf(creditPaise).multiply(feeFraction)
            ).paise();
            if (cancellationFee < 0) {
                cancellationFee = 0;
            }

            long computedRefund = Math.max(0L, creditPaise - cancellationFee);
            // Cap at amount_paid_paise
            computedRefund = Math.min(computedRefund, input.amountPaidPaise());

            explanation.add("Cancellation fee (%s%%): %d paise. Net computed refund: %d paise".formatted(
                    feePercent.stripTrailingZeros().toPlainString(), cancellationFee, computedRefund));

            // Cap by available refundable cash in chain
            if (computedRefund > input.totalCashRemainingInChain()) {
                cappedByCashPaise = input.totalCashRemainingInChain();
                refundPaise = input.totalCashRemainingInChain();
                explanation.add("Refund capped by available cash in payment chain to %d paise".formatted(cappedByCashPaise));
            } else {
                refundPaise = computedRefund;
            }
        } else {
            // Plan change workflow: amountDue = newPlanPrice - credit
            long net = input.newPlanPricePaise() - creditPaise;
            if (net > 0) {
                amountDuePaise = net;
                refundPaise = 0L;
                explanation.add("Plan upgrade amount due: %d paise (%d new plan price - %d credit)".formatted(
                        amountDuePaise, input.newPlanPricePaise(), creditPaise));
            } else if (net < 0) {
                amountDuePaise = 0L;
                long computedRefund = -net;
                // Cap at amount_paid_paise
                computedRefund = Math.min(computedRefund, input.amountPaidPaise());

                explanation.add("Plan downgrade credit exceeds price. Raw refund: %d paise".formatted(computedRefund));

                if (computedRefund > input.totalCashRemainingInChain()) {
                    cappedByCashPaise = input.totalCashRemainingInChain();
                    refundPaise = input.totalCashRemainingInChain();
                    explanation.add("Refund capped by available cash in payment chain to %d paise".formatted(cappedByCashPaise));
                } else {
                    refundPaise = computedRefund;
                }
            } else {
                amountDuePaise = 0L;
                refundPaise = 0L;
                explanation.add("Equal value plan change: 0 paise due, 0 paise refunded");
            }
        }

        return ProrationResult.builder()
                .daysRemaining(daysRemaining)
                .totalDays(totalDays)
                .sessionsRemaining(sessionsRemaining)
                .sessionsTotal(input.sessionsTotal())
                .daysRatio(daysRatio)
                .sessionsRatio(sessionsRatio)
                .refundableFraction(refundableFraction)
                .creditPaise(creditPaise)
                .amountDuePaise(amountDuePaise)
                .refundPaise(refundPaise)
                .cappedByCashPaise(cappedByCashPaise)
                .policy(POLICY_NAME)
                .explanation(explanation)
                .build();
    }
}
