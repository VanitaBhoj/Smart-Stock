package com.smartstock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_kafka_events")
public class ProcessedKafkaEvent {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedKafkaEvent() {
    }

    public ProcessedKafkaEvent(Long eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}
