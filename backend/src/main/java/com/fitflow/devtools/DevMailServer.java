package com.fitflow.devtools;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
public class DevMailServer {

    @Value("${spring.mail.port:3025}")
    private int port;

    private GreenMail greenMail;

    @PostConstruct
    public void start() {
        try {
            ServerSetup setup = new ServerSetup(port, null, ServerSetup.PROTOCOL_SMTP);
            greenMail = new GreenMail(setup);
            greenMail.start();
            log.info("GreenMail SMTP Dev Server started on port {}", port);
        } catch (Exception e) {
            log.warn("Could not start embedded GreenMail on port {}: {}", port, e.getMessage());
        }
    }

    public GreenMail getGreenMail() {
        return greenMail;
    }

    @PreDestroy
    public void stop() {
        if (greenMail != null) {
            greenMail.stop();
            log.info("GreenMail SMTP Dev Server stopped.");
        }
    }
}
