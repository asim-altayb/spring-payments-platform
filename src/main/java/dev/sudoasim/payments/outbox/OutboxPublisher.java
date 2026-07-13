package dev.sudoasim.payments.outbox;

import dev.sudoasim.payments.webhook.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository events;
    private final WebhookSigner signer;

    public OutboxPublisher(OutboxEventRepository events, WebhookSigner signer) {
        this.events = events;
        this.signer = signer;
    }

    @Scheduled(fixedDelayString = "${payments.outbox-poll-ms:1000}")
    @Transactional
    public void publishBatch() {
        for (OutboxEvent event : events.lockNextBatch()) {
            event.recordAttempt();
            String signature = signer.sign(event.getPayload());
            // Replace this boundary with an HTTP/message adapter in production.
            log.info("event={} signature={} payload={}", event.getId(), signature, event.getPayload());
            event.markPublished();
        }
    }
}
