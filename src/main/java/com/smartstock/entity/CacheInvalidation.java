package com.smartstock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cache_invalidations")
public class CacheInvalidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String cacheName;

    @Column(nullable = false, length = 255)
    private String cacheKey;

    @Column(nullable = false)
    private Instant createdAt;

    protected CacheInvalidation() {
    }

    public CacheInvalidation(String cacheName, String cacheKey, Instant createdAt) {
        this.cacheName = cacheName;
        this.cacheKey = cacheKey;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getCacheName() { return cacheName; }
    public String getCacheKey() { return cacheKey; }
}
