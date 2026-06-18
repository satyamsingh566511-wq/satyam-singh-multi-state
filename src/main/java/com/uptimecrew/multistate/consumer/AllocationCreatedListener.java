package com.uptimecrew.multistate.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/*
 * No @ConditionalOnBean(ConsumerFactory.class): that condition was evaluated during
 * component scanning, BEFORE Spring Boot's Kafka auto-configuration registered the
 * ConsumerFactory bean, so the listener was silently excluded and never joined the
 * consumer group (Spring's docs restrict @ConditionalOnBean to auto-configuration
 * classes for exactly this reason). spring-boot-starter-kafka now guarantees a
 * ConsumerFactory, so the guard is both unreliable and unnecessary.
 */
@Component
public class AllocationCreatedListener {

    private static final Logger LOG = LoggerFactory.getLogger(AllocationCreatedListener.class);

    private final TenantReadModelRepository readModelRepository;
    private final ObjectMapper mapper;

    public AllocationCreatedListener(TenantReadModelRepository readModelRepository, ObjectMapper mapper) {
        this.readModelRepository = readModelRepository;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "tenants.events",
            groupId = "multistate-read-model-builder",
            containerFactory = "kafkaListenerContainerFactory")
    public void onEvent(String payload) throws Exception {
        AllocationCreatedEvent event = mapper.readValue(payload, AllocationCreatedEvent.class);
        TenantReadModel document = readModelRepository.findById(event.aggregateId())
                .orElseGet(() -> new TenantReadModel(event.aggregateId(), null, null, null));
        document.applyEvent(event);
        readModelRepository.save(document);
        LOG.info("consumed AllocationCreated aggregateId={}", event.aggregateId());
    }
}
