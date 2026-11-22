package com.EOT.project.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes long-expired reset tokens once an hour. */
@Component
public class PasswordResetCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetCleanupJob.class);
    private final PasswordResetService service;

    public PasswordResetCleanupJob(PasswordResetService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
    public void purge() {
        try {
            int deleted = service.purgeExpired();
            if (deleted > 0) {
                log.info("Removed {} expired password reset tokens", deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not purge password reset tokens: {}", ex.getMessage());
        }
    }
}
