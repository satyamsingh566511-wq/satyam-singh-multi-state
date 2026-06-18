package com.uptimecrew.multistate.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;

/**
 * Smoke check for W3 D5 Task 2 — Kafka W3C trace-context propagation.
 *
 * <p>A tiny {@link ProducerListener} that logs the {@code traceparent} header on
 * every successful send, so propagation can be eyeballed in the bootRun log
 * without opening Jaeger. The {@code opentelemetry-spring-kafka-2.7}
 * instrumentation installs a {@code ProducerInterceptor} that injects the W3C
 * {@code traceparent} header <em>before</em> this listener fires — so if the
 * header is missing here, the instrumentation is not on the classpath or
 * {@code otel.instrumentation.spring-kafka.enabled} is false.
 *
 * <p>Spring Boot's Kafka auto-configuration auto-detects a single
 * {@code ProducerListener} bean and attaches it to the auto-configured
 * {@link org.springframework.kafka.core.KafkaTemplate}, replacing Boot's default
 * {@code LoggingProducerListener}. The type is intentionally <strong>raw</strong>:
 * Boot injects a {@code ProducerListener<Object, Object>} into the template, and a
 * {@code ProducerListener<String, String>} bean would not satisfy that
 * (generic-invariant) injection point.
 */
@Component
public final class TraceparentLoggingProducerListener implements ProducerListener {

    private static final Logger LOG = LoggerFactory.getLogger(TraceparentLoggingProducerListener.class);

    @Override
    public void onSuccess(ProducerRecord record, RecordMetadata recordMetadata) {
        Header header = record.headers().lastHeader("traceparent");
        if (header == null) {
            LOG.warn("outgoing kafka record has NO traceparent header topic={} key={}",
                    record.topic(), record.key());
            return;
        }
        LOG.info("outgoing traceparent={} topic={} key={}",
                new String(header.value()), record.topic(), record.key());
    }
}
