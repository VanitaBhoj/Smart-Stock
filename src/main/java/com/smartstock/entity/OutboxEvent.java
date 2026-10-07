package com.smartstock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = @Index(
        name = "idx_outbox_events_status_created", columnList = "status, created_at"
))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 40)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 1000)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String eventType,
            String aggregateType,
            Long aggregateId,
            String payload,
            Instant createdAt
    ) {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public OutboxEventStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }

    public void markProcessed(Instant processedAt) {
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = processedAt;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
