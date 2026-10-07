package com.smartstock.service;

import com.smartstock.entity.OutboxEvent;
import com.smartstock.entity.OutboxEventStatus;
import com.smartstock.repository.OutboxEventRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public OutboxEventProcessor(
            OutboxEventRepository repository,
            ApplicationEventPublisher publisher,
            Clock clock
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public boolean processOne(Long id) {
        OutboxEvent event = repository.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
            return false;
        }
        try {
            publisher.publishEvent(new OutboxBusinessEvent(
                    event.getId(), event.getEventType(), event.getAggregateType(),
                    event.getAggregateId(), event.getPayload(), event.getCreatedAt()
            ));
            event.markProcessed(clock.instant());
            repository.save(event);
            return true;
        } catch (RuntimeException ex) {
            event.markFailed(ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
            repository.save(event);
            log.warn("Outbox event {} failed on attempt {}; it remains pending for retry",
                    id, event.getAttempts(), ex);
            return false;
        }
    }
}
