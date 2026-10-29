package com.fitflow.membership;

import com.fitflow.membership.proration.MinRatioProrationPolicy;
import com.fitflow.membership.proration.ProrationInput;
import com.fitflow.membership.proration.ProrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProrationPropertyTest {

    private final MinRatioProrationPolicy policy = new MinRatioProrationPolicy();

    @Test
    @DisplayName("Proration Property Tests: 1000 iterations with fixed seed")
    void testProrationProperties1000Iterations() {
        Random random = new Random(42_424_242L);
        Instant now = Instant.parse("2026-10-28T12:00:00Z");

        for (int i = 0; i < 1000; i++) {
            int totalDays = 1 + random.nextInt(365);
            // offset can be positive or negative
            int secondsOffset = (random.nextInt(totalDays * 2 + 10) - 5) * 86400 + random.nextInt(86400);
            Instant endsAt = now.plusSeconds(secondsOffset);

            boolean isUnlimited = random.nextBoolean();
            Integer sessionsTotal = isUnlimited ? null : (1 + random.nextInt(100));
            int sessionsUsed = random.nextInt(sessionsTotal != null ? sessionsTotal * 2 : 100);

            long amountPaid = random.nextInt(1_000_000);
            long newPrice = random.nextInt(1_000_000);
            long cashInChain = random.nextInt(1_000_000);
            BigDecimal feePercent = BigDecimal.valueOf(random.nextInt(101));
            boolean isCancel = random.nextBoolean();

            ProrationInput input = ProrationInput.builder()
                    .now(now)
                    .endsAt(endsAt)
                    .totalDays(totalDays)
                    .sessionsTotal(sessionsTotal)
                    .sessionsUsed(sessionsUsed)
                    .amountPaidPaise(amountPaid)
                    .newPlanPricePaise(newPrice)
                    .totalCashRemainingInChain(cashInChain)
                    .feePercent(feePercent)
                    .isCancel(isCancel)
                    .build();

            ProrationResult result = policy.calculate(input);

            // Invariant 1: credit >= 0
            assertTrue(result.creditPaise() >= 0,
                    "Iteration " + i + ": creditPaise must be >= 0, was " + result.creditPaise());

            // Invariant 2: credit <= amountPaid
            assertTrue(result.creditPaise() <= amountPaid,
                    "Iteration " + i + ": creditPaise must be <= amountPaid, was " + result.creditPaise() + " vs " + amountPaid);

            // Invariant 3: fraction in [0, 1]
            assertTrue(result.refundableFraction().compareTo(BigDecimal.ZERO) >= 0,
                    "Iteration " + i + ": fraction must be >= 0");
            assertTrue(result.refundableFraction().compareTo(BigDecimal.ONE) <= 0,
                    "Iteration " + i + ": fraction must be <= 1");

            // Invariant 4: refund >= 0 and never > amountPaid
            assertTrue(result.refundPaise() >= 0,
                    "Iteration " + i + ": refund must be >= 0");
            assertTrue(result.refundPaise() <= amountPaid,
                    "Iteration " + i + ": refund must never exceed amountPaid");

            // Invariant 5: refund never > cash in chain
            assertTrue(result.refundPaise() <= cashInChain,
                    "Iteration " + i + ": refund must never exceed cash in chain");

            // Invariant 6: plan upgrade amountDue + credit == newPrice when upgrading
            if (!isCancel) {
                if (newPrice >= result.creditPaise()) {
                    assertEquals(newPrice, result.amountDuePaise() + result.creditPaise(),
                            "Iteration " + i + ": amountDue + credit must equal newPrice on upgrade");
                    assertEquals(0L, result.refundPaise());
                } else {
                    assertEquals(0L, result.amountDuePaise());
                }
            } else {
                assertEquals(0L, result.amountDuePaise());
            }
        }
    }
}
