package com.paperai.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public CacheManager cacheManager(RedissonClient redisson) {
        return new RedissonSpringCacheManager(redisson);
    }
}
