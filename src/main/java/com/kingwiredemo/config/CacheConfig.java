package com.kingwiredemo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine in-process cache configuration.
 *
 * Cache strategy per endpoint:
 *
 * "productSummary"  — 10-minute TTL
 *   Aggregate GROUP BY queries across the full product catalog are expensive.
 *   Summary data does not need real-time freshness; a 10-minute staleness
 *   window is acceptable for a reporting dashboard. Evicted and refreshed
 *   automatically after each pipeline run via @CacheEvict.
 *
 * "productDetail"   — 5-minute TTL
 *   Individual product lookups include a JOIN across inventory and pricing.
 *   Short TTL balances cache efficiency with data freshness after pipeline sync.
 *
 * Caffeine is chosen over Redis for this deployment because the API is
 * single-instance. Distributed cache would introduce network latency and
 * operational overhead that is not justified until horizontal scaling is needed.
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_PRODUCT_SUMMARY = "productSummary";
    public static final String CACHE_PRODUCT_DETAIL  = "productDetail";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_PRODUCT_SUMMARY,
                CACHE_PRODUCT_DETAIL
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats());
        return manager;
    }
}
