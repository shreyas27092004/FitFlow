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

    private static GreenMail sharedGreenMail;
    private static final Object LOCK = new Object();

    @PostConstruct
    public void start() {
        synchronized (LOCK) {
            if (sharedGreenMail == null) {
                try {
                    ServerSetup setup = new ServerSetup(port, null, ServerSetup.PROTOCOL_SMTP);
                    setup.setServerStartupTimeout(5000);
                    sharedGreenMail = new GreenMail(setup);
                    sharedGreenMail.start();
                    log.info("GreenMail SMTP Dev Server started on port {}", port);
                } catch (Exception e) {
                    log.warn("Could not start embedded GreenMail on port {}: {}", port, e.getMessage());
                }
            }
        }
    }

    public GreenMail getGreenMail() {
        return sharedGreenMail;
    }

    public void reset() {
        synchronized (LOCK) {
            if (sharedGreenMail != null) {
                try {
                    sharedGreenMail.purgeEmailFromAllMailboxes();
                } catch (Exception e) {
                    log.warn("Failed to purge GreenMail mailboxes: {}", e.getMessage());
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        // Intentionally keep sharedGreenMail alive across Spring TestContext reboots in same JVM.
        // It will be cleaned up on process termination.
    }
}
