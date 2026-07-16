package com.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * WHY CACHING?
 *
 * Every GET /products call:
 *   → hits our app → hits PostgreSQL → disk I/O → returns result
 *
 * Products don't change every millisecond. Fetching from DB every time is wasteful.
 *
 * With Redis cache:
 *   First request:  app → DB → store in Redis → return result
 *   Next 1000 requests: app → Redis (in-memory, sub-millisecond) → return result
 *   DB not touched at all
 *
 * WHY REDIS (not a HashMap in memory)?
 *   In-process HashMap cache:
 *     - Lost on app restart
 *     - Not shared across multiple app instances (scale to 3 servers = 3 caches)
 *     - No TTL (entries live forever)
 *   Redis:
 *     - Survives app restart
 *     - Shared across ALL app instances (one cache, many servers)
 *     - TTL: entries expire automatically (stale data evicted)
 *     - Persistence options: save to disk if needed
 *
 * @EnableCaching: activates Spring's caching proxy infrastructure.
 *   Without this, @Cacheable/@CacheEvict are silently ignored.
 *
 * Cache names we use:
 *   "products"   → TTL 10 minutes (products change occasionally)
 *   "categories" → TTL 30 minutes (categories rarely change)
 *   "product"    → single product by id
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${cache.ttl.products:600}")
    private long productsTtl;

    @Value("${cache.ttl.categories:1800}")
    private long categoriesTtl;

    private GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // Test Redis is actually reachable
            connectionFactory.getConnection().ping();

            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(redisSerializer()))
                    .disableCachingNullValues();

            Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                    "products",   defaults.entryTtl(Duration.ofSeconds(productsTtl)),
                    "product",    defaults.entryTtl(Duration.ofSeconds(productsTtl)),
                    "categories", defaults.entryTtl(Duration.ofSeconds(categoriesTtl)),
                    "category",   defaults.entryTtl(Duration.ofSeconds(categoriesTtl))
            );

            log.info("Redis connected — using RedisCacheManager");
            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(defaults)
                    .withInitialCacheConfigurations(cacheConfigs)
                    .build();

        } catch (Exception e) {
            // Redis not running → fall back to in-process cache (no TTL, not shared)
            // App still works — caching is an optimisation, not a hard dependency
            log.warn("Redis not available ({}). Falling back to in-memory cache. Start Redis for full caching.", e.getMessage());
            return new ConcurrentMapCacheManager("products", "product", "categories", "category");
        }
    }
}
