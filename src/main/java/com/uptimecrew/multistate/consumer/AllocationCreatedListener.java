package com.uptimecrew.multistate.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.multistate.readmodel.TenantReadModel;
import com.uptimecrew.multistate.readmodel.TenantReadModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ConsumerFactory.class)
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
