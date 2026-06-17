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

    @Scheduled(fixedDelay = 1000L)
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
