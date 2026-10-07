package com.smartstock.service;

import com.smartstock.entity.OutboxEvent;
import com.smartstock.entity.OutboxEventStatus;
import com.smartstock.repository.OutboxEventRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherJob {

    private final OutboxEventProcessor processor;
    private final OutboxEventRepository repository;

    public OutboxPublisherJob(OutboxEventProcessor processor, OutboxEventRepository repository) {
        this.processor = processor;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher-interval-ms:5000}")
    public void publishPendingEvents() {
        List<Long> pendingIds = repository.findByStatusOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING, PageRequest.of(0, 100)
                ).stream()
                .map(OutboxEvent::getId)
                .toList();
        pendingIds.forEach(processor::processOne);
    }
}
