package com.example.demo.docgen.controller;

import com.example.demo.docgen.service.CacheInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
public class AdminCacheController {
    private final CacheInspectionService inspectionService;
    private final CacheManager cacheManager;

    @GetMapping
    public ResponseEntity<List<String>> listCaches() {
        return ResponseEntity.ok(List.copyOf(cacheManager.getCacheNames()));
    }

    @GetMapping("/{cacheName}")
    public ResponseEntity<?> inspectCache(@PathVariable String cacheName) {
        return ResponseEntity.ok(inspectionService.inspectCache(cacheName));
    }

    @GetMapping("/all")
    public ResponseEntity<?> inspectAll() {
        return ResponseEntity.ok(inspectionService.inspectAllCaches());
    }

    /**
     * Evict a specific key from a cache. Provide either `key` (string) or
     * `namespace` + `templateId` to evict composite keys used by documentTemplates.
     */
    @DeleteMapping("/{cacheName}/key")
    public ResponseEntity<?> evictKey(
            @PathVariable String cacheName,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String templateId) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        Object evictKey = null;
        if (namespace != null && templateId != null) {
            evictKey = new org.springframework.cache.interceptor.SimpleKey(namespace, templateId);
        } else if (key != null) {
            evictKey = key;
        } else {
            return ResponseEntity.badRequest().body("Provide either 'key' or both 'namespace' and 'templateId'");
        }

        cache.evict(evictKey);
        return ResponseEntity.ok().build();
    }

    /**
     * Clear all entries from a specific cache
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<?> clearCache(@PathVariable String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }
        cache.clear();
        return ResponseEntity.ok().build();
    }

    /**
     * Clear all caches managed by the CacheManager
     */
    @DeleteMapping
    public ResponseEntity<?> clearAllCaches() {
        for (String name : cacheManager.getCacheNames()) {
            org.springframework.cache.Cache c = cacheManager.getCache(name);
            if (c != null) c.clear();
        }
        return ResponseEntity.ok().build();
    }
}
