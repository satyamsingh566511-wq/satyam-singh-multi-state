package com.uptimecrew.multistate.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final EventOutboxRepository repository;
    private final Optional<KafkaTemplate<String, String>> kafkaTemplate;

    public OutboxPublisher(EventOutboxRepository repository,
                           @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = Optional.ofNullable(kafkaTemplate);
    }

    // Delay is property-driven (defaults preserve the original fixed 1s sweep). Integration
    // tests that assert "exactly one trace id covers all emitted spans" set both to a large
    // value so the background sweep stays dormant and the test can drive ONE publish under a
    // known parent span — otherwise the 1s sweep keeps flooding the exporter with empty-poll
    // traces and the single-trace assertion can never hold.
    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}",
               initialDelayString = "${outbox.publisher.initial-delay-ms:0}")
    @Transactional
    public void publishPending() {
        if (kafkaTemplate.isEmpty()) {
            return;
        }
        List<EventOutboxEntity> batch =
                repository.findUnpublishedForUpdate(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        KafkaTemplate<String, String> template = kafkaTemplate.get();
        for (EventOutboxEntity row : batch) {
            try {
                template.send(row.getTopic(), row.getAggregateId(), row.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                row.markPublished(Instant.now());
                LOG.info("outbox published id={} topic={} aggregateId={}",
                         row.getId(), row.getTopic(), row.getAggregateId());
            } catch (Exception ex) {
                LOG.warn("outbox publish failed id={} topic={} cause={}",
                         row.getId(), row.getTopic(), ex.toString());
            }
        }
    }
}
