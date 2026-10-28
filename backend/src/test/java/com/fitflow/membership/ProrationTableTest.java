package com.fitflow.membership;

import com.fitflow.membership.proration.MinRatioProrationPolicy;
import com.fitflow.membership.proration.ProrationInput;
import com.fitflow.membership.proration.ProrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProrationTableTest {

    private final MinRatioProrationPolicy policy = new MinRatioProrationPolicy();

    record TestCase(
            int caseNumber,
            String description,
            ProrationInput input,
            BigDecimal expectedFraction,
            long expectedCredit,
            long expectedAmountDue,
            long expectedRefund,
            Long expectedCappedByCash
    ) {
    }

    private static Stream<TestCase> prorationTestCases() {
        Instant base = Instant.parse("2026-10-01T00:00:00Z");

        return Stream.of(
                // 1. day 0, 0 sessions used: fraction 1.0, credit 89900
                new TestCase(1, "Day 0, 0 sessions used: full fraction and credit",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(30 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(0)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("1.0000000000"), 89900L, 0L, 0L, null),

                // 2. day 15 of 30, 8 of 16 sessions left: fraction 0.5, credit 44950
                new TestCase(2, "Day 15 of 30, 8 of 16 left: 0.5 fraction",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 44950L, 0L, null),

                // 3. 15 days left, 16 of 16 sessions left: min(0.5, 1) = 0.5, credit 44950
                new TestCase(3, "15 days left, 16 of 16 sessions left: min(0.5, 1)=0.5",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(0)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 44950L, 0L, null),

                // 4. 30 days left, 4 of 16 left: min(1, 0.25)=0.25, credit 22475
                new TestCase(4, "30 days left, 4 of 16 left: min(1, 0.25)=0.25",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(30 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(12)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.2500000000"), 22475L, 67425L, 0L, null),

                // 5. 0 days left: credit 0
                new TestCase(5, "0 days left: credit 0",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base)
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(6)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        BigDecimal.ZERO.setScale(10), 0L, 89900L, 0L, null),

                // 6. all sessions used, 20 days left: credit 0
                new TestCase(6, "All sessions used, 20 days left: credit 0",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(20 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(16)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        BigDecimal.ZERO.setScale(10), 0L, 89900L, 0L, null),

                // 7. partial day: 10 hours before the end counts as 1 day remaining: 89900/30 = 2997
                new TestCase(7, "10 hours before end counts as 1 day remaining: fraction 1/30, credit 2997",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(10 * 3600L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(0)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.0333333333"), 2997L, 86903L, 0L, null),

                // 8. half-paisa rounding: exact credit ends in .5 and rounds UP
                new TestCase(8, "Half-paisa rounding: 10001 * 0.5 = 5000.5 rounds UP to 5001",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(10001L)
                                .newPlanPricePaise(10001L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.5000000000"), 5001L, 5000L, 0L, null),

                // 9. BASIC 49900 -> PRO 89900 with credit 24950: amountDue 64950
                new TestCase(9, "BASIC 49900 -> PRO 89900 with credit 24950: amountDue 64950",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(8)
                                .sessionsUsed(4)
                                .amountPaidPaise(49900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.5000000000"), 24950L, 64950L, 0L, null),

                // 10. PRO -> BASIC with credit 60000: amountDue 49900-60000 = -10100: refund 10100
                new TestCase(10, "PRO -> BASIC with credit 60000: refund 10100",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(30 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(null)
                                .sessionsUsed(0)
                                .amountPaidPaise(60000L)
                                .newPlanPricePaise(49900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("1.0000000000"), 60000L, 0L, 10100L, null),

                // 11. Same price switch: amountDue = price - credit
                new TestCase(11, "Same price switch: amountDue = 49900 - 24950 = 24950",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(8)
                                .sessionsUsed(4)
                                .amountPaidPaise(49900L)
                                .newPlanPricePaise(49900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.5000000000"), 24950L, 24950L, 0L, null),

                // 12. ELITE (unlimited) with 10 days left of 30: fraction uses days only = 1/3
                new TestCase(12, "ELITE unlimited with 10 days left: fraction 1/3, credit 49967",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(10 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(null)
                                .sessionsUsed(5)
                                .amountPaidPaise(149900L)
                                .newPlanPricePaise(149900L)
                                .totalCashRemainingInChain(150000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.3333333333"), 49967L, 99933L, 0L, null),

                // 13. Credit larger than price edge: refund never above amount paid
                new TestCase(13, "Credit larger than price edge: refund 86667",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(29 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(null)
                                .sessionsUsed(0)
                                .amountPaidPaise(100000L)
                                .newPlanPricePaise(10000L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.9666666667"), 96667L, 0L, 86667L, null),

                // 14. Cancel with 10% fee: credit 44950 -> fee 4495 -> refund 40455
                new TestCase(14, "Cancel with 10% fee: credit 44950 -> fee 4495 -> refund 40455",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(new BigDecimal("10"))
                                .isCancel(true)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 0L, 40455L, null),

                // 15. Leap-year boundary duration (29 days)
                new TestCase(15, "Leap-year boundary (29 days): 14 days left, credit 43400",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(14 * 86400L))
                                .totalDays(29)
                                .sessionsTotal(14)
                                .sessionsUsed(7)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.4827586207"), 43400L, 46500L, 0L, null),

                // 16. 1 day left of 30, 1 of 16 left: min(1/30, 1/16) = 1/30
                new TestCase(16, "1 day left of 30, 1 of 16 left: credit 2997",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(1 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(15)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.0333333333"), 2997L, 86903L, 0L, null),

                // 17. 1 day left of 30, 0 of 16 left: credit 0
                new TestCase(17, "1 day left of 30, 0 of 16 left: credit 0",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(1 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(16)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        BigDecimal.ZERO.setScale(10), 0L, 89900L, 0L, null),

                // 18. 29 days left of 30, 15 of 16 left: min(29/30, 15/16) = 15/16 = 0.9375
                new TestCase(18, "29 days left of 30, 15 of 16 left: fraction 0.9375, credit 84281",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(29 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(1)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.9375000000"), 84281L, 5619L, 0L, null),

                // 19. Cancel with 25% fee: credit 44950 -> fee 11238 -> refund 33712
                new TestCase(19, "Cancel with 25% fee: credit 44950 -> fee 11238 -> refund 33712",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(new BigDecimal("25"))
                                .isCancel(true)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 0L, 33712L, null),

                // 20. Cancel with 0% fee: credit 44950, refund 44950
                new TestCase(20, "Cancel with 0% fee: refund 44950",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(true)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 0L, 44950L, null),

                // 21. Cancel with 100% fee: credit 44950, refund 0
                new TestCase(21, "Cancel with 100% fee: refund 0",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(new BigDecimal("100"))
                                .isCancel(true)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 0L, 0L, null),

                // 22. Capped by cash remaining in chain: computed 44950 capped to 30000
                new TestCase(22, "Capped by cash in chain: refund capped to 30000",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(30000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(true)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 0L, 30000L, 30000L),

                // 23. Unlimited ELITE (149900) cancel with 5% fee, 15 days left: credit 74950, refund 71202
                new TestCase(23, "Unlimited ELITE cancel 5% fee, 15 days left: credit 74950, refund 71202",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(null)
                                .sessionsUsed(10)
                                .amountPaidPaise(149900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(200000L)
                                .feePercent(new BigDecimal("5"))
                                .isCancel(true)
                                .build(),
                        new BigDecimal("0.5000000000"), 74950L, 0L, 71202L, null),

                // 24. Unlimited ELITE 0 days left: credit 0
                new TestCase(24, "Unlimited ELITE 0 days left: credit 0",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(0))
                                .totalDays(30)
                                .sessionsTotal(null)
                                .sessionsUsed(10)
                                .amountPaidPaise(149900L)
                                .newPlanPricePaise(149900L)
                                .totalCashRemainingInChain(200000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        BigDecimal.ZERO.setScale(10), 0L, 149900L, 0L, null),

                // 25. BASIC 49900, 20 days left of 30, 2 of 8 sessions left: fraction 0.25, credit 12475
                new TestCase(25, "BASIC 49900, 20 days left, 2 of 8 sessions: fraction 0.25, credit 12475",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(20 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(8)
                                .sessionsUsed(6)
                                .amountPaidPaise(49900L)
                                .newPlanPricePaise(49900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.2500000000"), 12475L, 37425L, 0L, null),

                // 26. BASIC 49900, 10 days left of 30, 6 of 8 sessions left: fraction 1/3, credit 16633
                new TestCase(26, "BASIC 49900, 10 days left, 6 of 8 sessions: fraction 1/3, credit 16633",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(10 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(8)
                                .sessionsUsed(2)
                                .amountPaidPaise(49900L)
                                .newPlanPricePaise(49900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.3333333333"), 16633L, 33267L, 0L, null),

                // 27. Change PRO 89900 to ELITE 149900, credit 44950: amountDue 104950
                new TestCase(27, "Change PRO to ELITE, credit 44950: amountDue 104950",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(15 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(8)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(149900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.5000000000"), 44950L, 104950L, 0L, null),

                // 28. Change ELITE 150000 to BASIC 49900, credit 100000: refund 50100
                new TestCase(28, "Change ELITE to BASIC, credit 100000: refund 50100",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(20 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(null)
                                .sessionsUsed(5)
                                .amountPaidPaise(150000L)
                                .newPlanPricePaise(49900L)
                                .totalCashRemainingInChain(150000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.6666666667"), 100000L, 0L, 50100L, null),

                // 29. 1 second before end counts as 1 day remaining: credit 2997
                new TestCase(29, "1 second before end counts as 1 day remaining: credit 2997",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(1))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(0)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(89900L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(BigDecimal.ZERO)
                                .isCancel(false)
                                .build(),
                        new BigDecimal("0.0333333333"), 2997L, 86903L, 0L, null),

                // 30. Day 0 cancel with 10% fee: credit 89900, fee 8990, refund 80910
                new TestCase(30, "Day 0 cancel with 10% fee: credit 89900, refund 80910",
                        ProrationInput.builder()
                                .now(base)
                                .endsAt(base.plusSeconds(30 * 86400L))
                                .totalDays(30)
                                .sessionsTotal(16)
                                .sessionsUsed(0)
                                .amountPaidPaise(89900L)
                                .newPlanPricePaise(0L)
                                .totalCashRemainingInChain(100000L)
                                .feePercent(new BigDecimal("10"))
                                .isCancel(true)
                                .build(),
                        new BigDecimal("1.0000000000"), 89900L, 0L, 80910L, null)
        );
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("prorationTestCases")
    @DisplayName("Proration exact table verification (30 cases)")
    void testProrationTableCases(TestCase tc) {
        ProrationResult result = policy.calculate(tc.input());

        assertEquals(tc.expectedFraction(), result.refundableFraction(),
                "Case " + tc.caseNumber() + ": wrong refundableFraction");
        assertEquals(tc.expectedCredit(), result.creditPaise(),
                "Case " + tc.caseNumber() + ": wrong creditPaise");
        assertEquals(tc.expectedAmountDue(), result.amountDuePaise(),
                "Case " + tc.caseNumber() + ": wrong amountDuePaise");
        assertEquals(tc.expectedRefund(), result.refundPaise(),
                "Case " + tc.caseNumber() + ": wrong refundPaise");
        assertEquals(tc.expectedCappedByCash(), result.cappedByCashPaise(),
                "Case " + tc.caseNumber() + ": wrong cappedByCashPaise");
    }
}
