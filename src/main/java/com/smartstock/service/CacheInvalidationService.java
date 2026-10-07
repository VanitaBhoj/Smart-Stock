package com.smartstock.service;

import com.smartstock.entity.CacheInvalidation;
import com.smartstock.repository.CacheInvalidationRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CacheInvalidationService {

    private final CacheInvalidationRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public CacheInvalidationService(
            CacheInvalidationRepository repository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String cacheName, Object key) {
        CacheInvalidation event = repository.saveAndFlush(
                new CacheInvalidation(cacheName, String.valueOf(key), clock.instant())
        );
        eventPublisher.publishEvent(new CacheInvalidationQueued(event));
    }
}
