package com.fitflow.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine mailTemplateEngine;
    private final EmailRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final Environment environment;
    private final java.time.Clock clock;

    private final String fromAddress;
    private final String frontendBaseUrl;
    private final String displayTimezone;

    private Counter sentCounter;
    private Counter failedCounter;
    private Counter skippedCounter;

    public EmailService(
            JavaMailSender mailSender,
            @Qualifier("mailTemplateEngine") TemplateEngine mailTemplateEngine,
            EmailRateLimiter rateLimiter,
            MeterRegistry meterRegistry,
            Environment environment,
            java.time.Clock clock,
            @Value("${fitflow.mail.from:no-reply@fitflow.local}") String fromAddress,
            @Value("${fitflow.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:3000}}") String frontendBaseUrl,
            @Value("${fitflow.display-timezone:Asia/Kolkata}") String displayTimezone) {
        this.mailSender = mailSender;
        this.mailTemplateEngine = mailTemplateEngine;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.environment = environment;
        this.clock = clock;
        this.fromAddress = fromAddress;
        this.frontendBaseUrl = frontendBaseUrl;
        this.displayTimezone = displayTimezone;
    }

    @PostConstruct
    public void init() {
        if (meterRegistry != null) {
            sentCounter = meterRegistry.counter("mail.sent");
            failedCounter = meterRegistry.counter("mail.failed");
            skippedCounter = meterRegistry.counter("mail.skipped");
        }

        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd) {
            String host = environment.getProperty("spring.mail.host");
            String username = environment.getProperty("spring.mail.username");
            String password = environment.getProperty("spring.mail.password");
            if (host == null || host.isBlank() || username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalStateException("Production startup failed: SMTP host, username, and password must not be blank.");
            }
        }
    }

    public boolean sendEmail(Long userId, String toEmail, String templateName, String subject, Map<String, Object> variables) {
        if (userId != null && !rateLimiter.isAllowed(userId, clock.instant())) {
            log.warn("Rate limit exceeded for user id {}: dropped email for template {}", userId, templateName);
            if (skippedCounter != null) {
                skippedCounter.increment();
            }
            return false;
        }

        try {
            Context context = new Context();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }
            context.setVariable("frontendBaseUrl", frontendBaseUrl);

            String htmlContent = mailTemplateEngine.process(templateName + ".html", context);
            String textContent = mailTemplateEngine.process(templateName + ".txt", context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textContent, htmlContent);

            mailSender.send(mimeMessage);

            if (sentCounter != null) {
                sentCounter.increment();
            }
            log.info("Sent email template='{}' to userId={} successfully", templateName, userId);
            return true;
        } catch (Exception e) {
            if (failedCounter != null) {
                failedCounter.increment();
            }
            log.error("Failed to send email template='{}' to userId={}: {}", templateName, userId, e.getMessage());
            throw new RuntimeException("Failed to send email template " + templateName + ": " + e.getMessage(), e);
        }
    }

    public String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        ZoneId zone = ZoneId.of(displayTimezone);
        ZonedDateTime zdt = instant.atZone(zone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z");
        return zdt.format(formatter);
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }
}
