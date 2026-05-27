package com.paperai.cache;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LlmCacheService {

    private static final String PREFIX = "paperai:llm:";
    private static final long TTL_MINUTES = 30;

    @Value("${paperai.cache.llm.enabled:true}")
    private boolean enabled;

    @Autowired(required = false)
    private RedissonClient redisson;

    public String computeKey(String systemPrompt, String userMessage) {
        String input = (systemPrompt != null ? systemPrompt : "")
                + "|||"
                + (userMessage != null ? userMessage : "");
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    public String get(String key) {
        if (!enabled || redisson == null) return null;
        RBucket<String> bucket = redisson.getBucket(PREFIX + key);
        String hit = bucket.get();
        if (hit != null) {
            log.info("[LLMCache] 命中 key={}", key);
        }
        return hit;
    }

    public void put(String key, String response) {
        if (!enabled || redisson == null) return;
        RBucket<String> bucket = redisson.getBucket(PREFIX + key);
        bucket.set(response, TTL_MINUTES, TimeUnit.MINUTES);
    }
}
