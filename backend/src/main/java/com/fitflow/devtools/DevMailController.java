package com.fitflow.devtools;

import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/dev/mail")
@Profile("dev")
@RequiredArgsConstructor
public class DevMailController {

    private final DevMailServer devMailServer;

    public record EmailMessageDto(
            String subject,
            List<String> to,
            String from,
            String sentDate,
            String content
    ) {}

    @GetMapping
    public ResponseEntity<List<EmailMessageDto>> getReceivedEmails() {
        if (devMailServer.getGreenMail() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        MimeMessage[] messages = devMailServer.getGreenMail().getReceivedMessages();
        List<EmailMessageDto> list = new ArrayList<>();

        for (MimeMessage msg : messages) {
            try {
                String subject = msg.getSubject();
                Address[] recipients = msg.getAllRecipients();
                List<String> toList = recipients != null
                        ? Arrays.stream(recipients).map(Address::toString).toList()
                        : List.of();
                Address[] from = msg.getFrom();
                String fromStr = from != null && from.length > 0 ? from[0].toString() : "unknown";
                String sentDate = msg.getSentDate() != null ? msg.getSentDate().toString() : "";
                String content = msg.getContent() != null ? msg.getContent().toString() : "";

                list.add(new EmailMessageDto(subject, toList, fromStr, sentDate, content));
            } catch (Exception e) {
                log.warn("Failed to parse dev email message: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(list);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearEmails() {
        if (devMailServer.getGreenMail() != null) {
            devMailServer.getGreenMail().reset();
        }
        return ResponseEntity.noContent().build();
    }
}
