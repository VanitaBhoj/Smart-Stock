package com.smartstock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.entity.ProcessedKafkaEvent;
import com.smartstock.repository.ProcessedKafkaEventRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SmartStockKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(SmartStockKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProcessedKafkaEventRepository processedEvents;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public SmartStockKafkaConsumer(
            ObjectMapper objectMapper,
            ProcessedKafkaEventRepository processedEvents,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.processedEvents = processedEvents;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "${smartstock.kafka.business-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void consume(String json) {
        final OutboxBusinessEvent event;
        try {
            event = objectMapper.readValue(json, OutboxBusinessEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid SmartStock business event payload", ex);
        }
        if (event.id() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Business event must include id and eventType");
        }
        if (processedEvents.existsById(event.id())) {
            log.debug("Ignoring duplicate Kafka business event {}", event.id());
            return;
        }

        processedEvents.saveAndFlush(new ProcessedKafkaEvent(
                event.id(), event.eventType(), clock.instant()
        ));
        applicationEventPublisher.publishEvent(new KafkaBusinessEventReceived(event));
        log.info("Consumed SmartStock business event {} ({})", event.id(), event.eventType());
    }
}
