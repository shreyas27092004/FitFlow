package com.fitflow.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        MailTemplateConfig config = new MailTemplateConfig();
        templateEngine = config.mailTemplateEngine();
    }

    private static final String[] ALL_19_TEMPLATES = {
            "welcome-created",
            "password-reset",
            "account-suspended",
            "booking-confirmed",
            "booking-cancelled",
            "waitlist-promoted",
            "session-cancelled",
            "session-rescheduled",
            "session-reminder",
            "membership-started",
            "membership-changed",
            "membership-expiring",
            "membership-expired",
            "payment-receipt",
            "payment-failed",
            "refund-needs-approval",
            "refund-issued",
            "payout-approved",
            "trainer-pain-flag"
    };

    @ParameterizedTest(name = "Render template: {0}")
    @ValueSource(strings = {
            "welcome-created",
            "password-reset",
            "account-suspended",
            "booking-confirmed",
            "booking-cancelled",
            "waitlist-promoted",
            "session-cancelled",
            "session-rescheduled",
            "session-reminder",
            "membership-started",
            "membership-changed",
            "membership-expiring",
            "membership-expired",
            "payment-receipt",
            "payment-failed",
            "refund-needs-approval",
            "refund-issued",
            "payout-approved",
            "trainer-pain-flag"
    })
    @DisplayName("Assert all 19 email templates render HTML and PlainText without unresolved placeholders")
    void testAllTemplatesRenderHtmlAndTxt(String templateName) {
        Context context = new Context();
        context.setVariable("firstName", "John");
        context.setVariable("loginUrl", "http://localhost:3000/login");
        context.setVariable("tempPassword", "TestTempPass123");
        context.setVariable("resetUrl", "http://localhost:3000/reset-password?token=abc");
        context.setVariable("reason", "Schedule Conflict");
        context.setVariable("className", "Power Yoga");
        context.setVariable("sessionStartTime", "28 Oct 2026, 10:00 AM IST");
        context.setVariable("trainerName", "Sarah Connor");
        context.setVariable("oldStartTime", "28 Oct 2026, 09:00 AM IST");
        context.setVariable("newStartTime", "28 Oct 2026, 10:00 AM IST");
        context.setVariable("planName", "PRO Monthly");
        context.setVariable("oldPlanName", "BASIC");
        context.setVariable("newPlanName", "PRO");
        context.setVariable("creditFormatted", "Rs 200.00");
        context.setVariable("amountDueFormatted", "Rs 400.00");
        context.setVariable("refundFormatted", "Rs 0.00");
        context.setVariable("endsAt", "28 Nov 2026, 11:59 PM IST");
        context.setVariable("invoiceNumber", "INV-2026-001");
        context.setVariable("amountFormatted", "Rs 499.00");
        context.setVariable("description", "Fitness Monthly Membership");
        context.setVariable("errorMessage", "Card declined by issuer");
        context.setVariable("refundId", "REF-889");
        context.setVariable("periodStart", "01 Oct 2026");
        context.setVariable("periodEnd", "15 Oct 2026");
        context.setVariable("sessionDate", "28 Oct 2026");
        context.setVariable("areaCode", "LOWER_BACK");
        context.setVariable("unsubscribeUrl", "http://localhost:3000/me/notifications/unsubscribe");

        String html = templateEngine.process(templateName + ".html", context);
        assertNotNull(html);
        assertTrue(html.contains("John"));
        assertFalse(html.contains("${"));

        String txt = templateEngine.process(templateName + ".txt", context);
        assertNotNull(txt);
        assertTrue(txt.contains("John"));
        assertFalse(txt.contains("[("));
    }

    @Test
    @DisplayName("HTML template escapes XSS script tags in dynamic variables")
    void testHtmlEscaping() {
        Context context = new Context();
        context.setVariable("firstName", "<script>alert('xss')</script>");
        context.setVariable("className", "<img src=x onerror=alert(1)>");
        context.setVariable("sessionStartTime", "2026-10-28");
        context.setVariable("trainerName", "<b>BoldTrainer</b>");

        String html = templateEngine.process("booking-confirmed.html", context);
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;") || html.contains("&lt;script&gt;alert('xss')&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert('xss')</script>"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 99L, 100L, 49900L, 100000000L})
    @DisplayName("Verify template renders money vectors formatted via MoneyFormatter")
    void testTemplateMoneyVectors(long paise) {
        String formatted = MoneyFormatter.formatPaise(paise);

        Context context = new Context();
        context.setVariable("firstName", "Alice");
        context.setVariable("invoiceNumber", "INV-101");
        context.setVariable("amountFormatted", formatted);
        context.setVariable("description", "Subscription");

        String html = templateEngine.process("payment-receipt.html", context);
        assertTrue(html.contains(formatted));

        String txt = templateEngine.process("payment-receipt.txt", context);
        assertTrue(txt.contains(formatted));
    }
}
