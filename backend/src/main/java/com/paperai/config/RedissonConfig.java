package com.paperai.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedissonConfig {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public CacheManager cacheManager(RedissonClient redisson,
                                     @Value("${paperai.cache.default-ttl:600}") long defaultTtl) {
        Map<String, CacheConfig> config = new HashMap<>();

        // 实体缓存 — TTL(生存时间) / maxIdle(最大空闲时间)
        config.put("papers", new CacheConfig(defaultTtl * 3, defaultTtl));
        config.put("paperVersions", new CacheConfig(defaultTtl * 3, defaultTtl));
        config.put("users", new CacheConfig(defaultTtl * 6, defaultTtl));
        config.put("flowDefinitions", new CacheConfig(defaultTtl * 3, defaultTtl));
        config.put("knowledgeGraphs", new CacheConfig(defaultTtl * 3, defaultTtl));
        config.put("customAgents", new CacheConfig(defaultTtl * 3, defaultTtl));
        config.put("agentTasks", new CacheConfig(defaultTtl, defaultTtl / 2));
        config.put("references", new CacheConfig(defaultTtl * 3, defaultTtl));
        config.put("knowledgeDocs", new CacheConfig(defaultTtl * 3, defaultTtl));

        return new RedissonSpringCacheManager(redisson, config);
    }
}
