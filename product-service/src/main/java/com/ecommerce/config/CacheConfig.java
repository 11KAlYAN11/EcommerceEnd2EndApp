package com.ecommerce.config;

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
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/** Same shape as the monolith's CacheConfig (ADR B-10). Redis instance is
 *  shared with the monolith, but `spring.data.redis.database` in
 *  application.properties points this service at a different logical DB
 *  index — otherwise both would fight over the same "products"/"category"
 *  cache keys. */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${cache.ttl.products:600}")
    private long productsTtl;

    @Value("${cache.ttl.categories:1800}")
    private long categoriesTtl;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            connectionFactory.getConnection().ping();

            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new JdkSerializationRedisSerializer()))
                    .disableCachingNullValues();

            Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                    "products", defaults.entryTtl(Duration.ofSeconds(productsTtl)),
                    "product", defaults.entryTtl(Duration.ofSeconds(productsTtl)),
                    "categories", defaults.entryTtl(Duration.ofSeconds(categoriesTtl)),
                    "category", defaults.entryTtl(Duration.ofSeconds(categoriesTtl))
            );

            log.info("Redis connected — using RedisCacheManager");
            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(defaults)
                    .withInitialCacheConfigurations(cacheConfigs)
                    .enableStatistics()
                    .build();

        } catch (Exception e) {
            log.warn("Redis not available ({}). Falling back to in-memory cache.", e.getMessage());
            return new ConcurrentMapCacheManager("products", "product", "categories", "category");
        }
    }
}
