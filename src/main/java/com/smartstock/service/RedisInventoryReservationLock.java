package com.smartstock.service;

import com.smartstock.exception.InventoryReservationLockBusyException;
import com.smartstock.exception.InventoryReservationLockUnavailableException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RedisInventoryReservationLock implements InventoryReservationLock {

    private static final Logger log = LoggerFactory.getLogger(RedisInventoryReservationLock.class);
    private static final String LOCKS_RESOURCE_KEY = RedisInventoryReservationLock.class.getName();
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class
    );

    private final StringRedisTemplate redis;
    private final Duration lease;
    private final String keyPrefix;

    public RedisInventoryReservationLock(
            StringRedisTemplate redis,
            @Value("${smartstock.inventory-lock.lease-duration-ms:30000}") long leaseMillis,
            @Value("${smartstock.inventory-lock.key-prefix:smartstock:lock:inventory:}") String keyPrefix
    ) {
        if (leaseMillis < 1) {
            throw new IllegalArgumentException("Inventory lock lease must be positive");
        }
        this.redis = redis;
        this.lease = Duration.ofMillis(leaseMillis);
        this.keyPrefix = keyPrefix;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void lockProduct(Long productId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Inventory locks must be acquired inside a transaction");
        }

        Map<String, String> heldLocks = (Map<String, String>)
                TransactionSynchronizationManager.getResource(LOCKS_RESOURCE_KEY);
        String key = keyPrefix + productId;
        if (heldLocks != null && heldLocks.containsKey(key)) {
            return;
        }

        String token = UUID.randomUUID().toString();
        final Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(key, token, lease);
        } catch (DataAccessException ex) {
            throw new InventoryReservationLockUnavailableException(ex);
        }
        if (!Boolean.TRUE.equals(acquired)) {
            throw new InventoryReservationLockBusyException(productId);
        }

        if (heldLocks == null) {
            heldLocks = new HashMap<>();
            TransactionSynchronizationManager.bindResource(LOCKS_RESOURCE_KEY, heldLocks);
            Map<String, String> transactionLocks = heldLocks;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        transactionLocks.forEach(RedisInventoryReservationLock.this::release);
                    } finally {
                        if (TransactionSynchronizationManager.hasResource(LOCKS_RESOURCE_KEY)) {
                            TransactionSynchronizationManager.unbindResource(LOCKS_RESOURCE_KEY);
                        }
                    }
                }
            });
        }
        heldLocks.put(key, token);
    }

    private void release(String key, String token) {
        try {
            redis.execute(RELEASE_IF_OWNER, java.util.List.of(key), token);
        } catch (DataAccessException ex) {
            // The lease expires automatically. Do not turn a committed DB operation into an API failure.
            log.warn("Could not release inventory lock {}; its lease will expire", key, ex);
        }
    }
}
