package com.example.demo.docgen.service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CacheInspectionService {
    private final CacheManager cacheManager;

    public Map<String, Object> inspectCache(String cacheName) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cacheName", cacheName);

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            out.put("error", "Cache not found");
            return out;
        }

        out.put("springCacheClass", cache.getClass().getName());

        if (cache instanceof org.springframework.cache.caffeine.CaffeineCache) {
            @SuppressWarnings("unchecked")
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                    (com.github.benmanes.caffeine.cache.Cache<Object, Object>)
                            ((org.springframework.cache.caffeine.CaffeineCache) cache).getNativeCache();

            out.put("estimatedSize", nativeCache.estimatedSize());
            out.put("keys", nativeCache.asMap().keySet());

            CacheStats stats = nativeCache.stats();
            Map<String, Object> statsMap = new LinkedHashMap<>();
            statsMap.put("hitCount", stats.hitCount());
            statsMap.put("missCount", stats.missCount());
            statsMap.put("loadSuccessCount", stats.loadSuccessCount());
            statsMap.put("loadFailureCount", stats.loadFailureCount());
            statsMap.put("totalLoadTimeNanos", stats.totalLoadTime());
            statsMap.put("evictionCount", stats.evictionCount());
            statsMap.put("hitRate", stats.hitRate());
            out.put("stats", statsMap);
        } else {
            out.put("message", "Cache is not a CaffeineCache; native inspection unavailable");
        }

        return out;
    }

    public List<Map<String, Object>> inspectAllCaches() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : cacheManager.getCacheNames()) {
            list.add(inspectCache(name));
        }
        return list;
    }
}
