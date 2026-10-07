package com.smartstock.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class IsolatedCacheConfiguration {

    @Bean
    @Primary
    CacheManager isolatedCacheManager() {
        return new ConcurrentMapCacheManager(
                "productById", "productBySku", "inventoryByProductId"
        );
    }
}
