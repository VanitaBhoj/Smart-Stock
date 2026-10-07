package com.smartstock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOutboxProducer {

    private static final Set<String> KAFKA_EVENT_TYPES = Set.of(
            "ORDER_CREATED", "ORDER_CONFIRMED", "ORDER_CANCELLED", "RESERVATION_EXPIRED"
    );

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaOutboxProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${smartstock.kafka.business-events-topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @EventListener
    public void publishCommittedOutboxEvent(OutboxBusinessEvent event) {
        if (!KAFKA_EVENT_TYPES.contains(event.eventType())) {
            return;
        }
        String key = event.aggregateType() + ":" + event.aggregateId();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json).get(10, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize outbox event " + event.id(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event " + event.id(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Could not publish outbox event " + event.id(), ex);
        }
    }
}
