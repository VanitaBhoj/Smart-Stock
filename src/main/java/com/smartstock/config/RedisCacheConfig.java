package com.smartstock.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.dto.InventoryResponse;
import com.smartstock.dto.ProductResponse;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper
    ) {
        Jackson2JsonRedisSerializer<ProductResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ProductResponse.class);
        RedisCacheConfiguration productCache = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));

        Jackson2JsonRedisSerializer<InventoryResponse> inventoryValueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, InventoryResponse.class);
        RedisCacheConfiguration inventoryCache = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(inventoryValueSerializer));

        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(productCache)
                .withCacheConfiguration("productById", productCache)
                .withCacheConfiguration("productBySku", productCache)
                .withCacheConfiguration("inventoryByProductId", inventoryCache)
                .build();

        // Apply cache writes/evictions only after the surrounding database transaction commits.
        return new TransactionAwareCacheManagerProxy(redisCacheManager);
    }
}
