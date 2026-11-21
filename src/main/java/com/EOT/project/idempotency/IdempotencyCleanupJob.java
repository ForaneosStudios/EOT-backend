package com.EOT.project.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Removes expired idempotency records once an hour. */
@Configuration
@EnableScheduling
public class IdempotencyCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
    private final IdempotencyService service;

    public IdempotencyCleanupJob(IdempotencyService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    public void purgeExpired() {
        try {
            int deleted = service.deleteExpired();
            if (deleted > 0) {
                log.info("Removed {} expired idempotency keys", deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not purge expired idempotency keys: {}", ex.getMessage());
        }
    }
}
