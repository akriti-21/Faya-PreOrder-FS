package com.yourorg.foodorder.cache;

import com.yourorg.foodorder.config.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin caching facade.
 *
 * Existing services call methods here to benefit from Redis caching
 * WITHOUT any changes to the service classes themselves.
 *
 * Usage example (call from controller or service):
 *
 *   cacheableFacade.evictVendors();          // after vendor save
 *   cacheableFacade.evictMenuItems(vendorId);// after menu item save
 */
@Component
public class CacheableFacade {

    private static final Logger log = LoggerFactory.getLogger(CacheableFacade.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheableFacade(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Vendor cache helpers ──────────────────────────────────────────────────

    @Cacheable(value = RedisConfig.CACHE_VENDORS, key = "'all'")
    public <T> T cacheVendorList(java.util.function.Supplier<T> loader) {
        log.debug("Cache MISS: vendors:all — loading from DB");
        return loader.get();
    }

    @CacheEvict(value = RedisConfig.CACHE_VENDORS, allEntries = true)
    public void evictVendors() {
        log.debug("Cache EVICT: vendors (all entries)");
    }

    // ── Menu item cache helpers ───────────────────────────────────────────────

    @Cacheable(value = RedisConfig.CACHE_MENU_ITEMS, key = "#vendorId")
    public <T> T cacheMenuItems(UUID vendorId, java.util.function.Supplier<T> loader) {
        log.debug("Cache MISS: menuItems:{} — loading from DB", vendorId);
        return loader.get();
    }

    @CacheEvict(value = RedisConfig.CACHE_MENU_ITEMS, key = "#vendorId")
    public void evictMenuItems(UUID vendorId) {
        log.debug("Cache EVICT: menuItems:{}", vendorId);
    }

    // ── Analytics cache helpers ───────────────────────────────────────────────

    @Cacheable(value = RedisConfig.CACHE_ANALYTICS, key = "#metricKey")
    public <T> T cacheAnalytics(String metricKey, java.util.function.Supplier<T> loader) {
        log.debug("Cache MISS: analytics:{} — loading from DB", metricKey);
        return loader.get();
    }

    @CacheEvict(value = RedisConfig.CACHE_ANALYTICS, allEntries = true)
    public void evictAnalytics() {
        log.debug("Cache EVICT: analytics (all entries)");
    }
}