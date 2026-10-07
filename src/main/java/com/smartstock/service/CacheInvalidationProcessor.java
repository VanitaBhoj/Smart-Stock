package com.smartstock.service;

import com.smartstock.entity.CacheInvalidation;
import com.smartstock.repository.CacheInvalidationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class CacheInvalidationProcessor {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationProcessor.class);
    private final CacheInvalidationRepository repository;
    private final CacheManager cacheManager;

    public CacheInvalidationProcessor(CacheInvalidationRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedDelayString = "${cache.invalidation-scan-interval-ms:5000}")
    public void processPendingInvalidations() {
        List<CacheInvalidation> events = repository.findAllByOrderByIdAsc(PageRequest.of(0, 100));
        for (CacheInvalidation event : events) {
            if (!processOne(event)) return;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processAfterCommit(CacheInvalidationQueued event) {
        processOne(event.invalidation());
    }

    private boolean processOne(CacheInvalidation event) {
        try {
            Cache cache = cacheManager.getCache(event.getCacheName());
            if (cache == null) {
                throw new IllegalStateException("Unknown cache: " + event.getCacheName());
            }
            Object key = switch (event.getCacheName()) {
                case "productById", "inventoryByProductId" -> Long.valueOf(event.getCacheKey());
                default -> event.getCacheKey();
            };
            cache.evict(key);
            repository.deleteById(event.getId());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Cache invalidation {} remains queued for retry", event.getId(), ex);
            return false;
        }
    }
}
