package com.mmcove.agent.infra.redis;

import com.mmcove.agent.common.model.entity.ConversationMessage;
import com.mmcove.agent.common.util.JacksonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文 Redis 存储，用于热会话缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryStore {

    private static final String KEY_PREFIX = "mmcove:conversation:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(120);

    private final RedisService redisService;

    public void saveMessages(String sessionId, List<ConversationMessage> messages) {
        String key = KEY_PREFIX + sessionId;
        String json = JacksonUtils.toJson(messages);
        redisService.set(key, json, DEFAULT_TTL);
    }

    @SuppressWarnings("unchecked")
    public List<ConversationMessage> loadMessages(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        return redisService.get(key, String.class)
                .map(json -> JacksonUtils.fromJson(json, List.class))
                .orElse(List.of());
    }

    public void evict(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        redisService.delete(key);
    }

    public boolean hasCache(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        return redisService.hasKey(key);
    }
}
