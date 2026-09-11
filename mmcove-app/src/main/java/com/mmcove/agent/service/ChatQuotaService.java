package com.mmcove.agent.service;

import com.mmcove.agent.common.context.TokenAuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 聊天配额管理服务。
 * 免费用户使用 Redis 每日计数器限制调用次数，
 * 以 用户ID（userId）作为唯一标识。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatQuotaService {

    private final StringRedisTemplate redisTemplate;

    /** 免费用户每日调用上限 */
    private static final int FREE_DAILY_LIMIT = 20;

    /** Redis Key 前缀 */
    private static final String KEY_PREFIX = "chat:daily:";

    /**
     * 检查并增加每日免费次数。
     *
     * @param userId 用户ID（登录返回的唯一标识）
     * @return true 表示允许，false 表示已达上限
     */
    public boolean checkAndIncrementDailyLimit(String userId) {
        String key = buildDailyKey(userId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 首次计数，设置过期时间到当天结束
            redisTemplate.expire(key, Duration.ofDays(1).plusHours(1));
        }
        if (count != null && count > FREE_DAILY_LIMIT) {
            log.info("[配额检查] 用户{}今日已用{}次，超过免费上限{}", userId, count, FREE_DAILY_LIMIT);
            return false;
        }
        return true;
    }

    /**
     * 获取当日已用次数。
     */
    public int getDailyUsed(String userId) {
        String key = buildDailyKey(userId);
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? 0 : Integer.parseInt(val);
    }

    /**
     * 获取配额信息。
     *
     * @param userId    用户ID（会话模式时使用）
     * @param tokenContext Token认证上下文（api-token模式时使用）
     * @return 配额信息Map
     */
    public Map<String, Object> getQuotaInfo(String userId, TokenAuthContext tokenContext) {
        Map<String, Object> info = new HashMap<>();

        boolean useApiToken = tokenContext != null && tokenContext.getTokenId() != null;

        if (useApiToken) {
            info.put("mode", "token");
            info.put("tokenId", tokenContext.getTokenId());
            info.put("tokenName", tokenContext.getTokenName());
            int used = tokenContext.getUsedQuota() != null ? tokenContext.getUsedQuota() : 0;
            int remain = tokenContext.getRemainQuota() != null ? tokenContext.getRemainQuota() : 0;
            if (tokenContext.isUnlimitedQuota()) {
                info.put("used", used);
                info.put("limit", -1);
                info.put("unlimited", true);
            } else {
                info.put("used", used);
                info.put("limit", used + remain);
                info.put("remain", remain);
                info.put("unlimited", false);
            }
        } else {
            int used = getDailyUsed(userId);
            info.put("mode", "free");
            info.put("used", used);
            info.put("limit", FREE_DAILY_LIMIT);
            info.put("unlimited", false);
            // 次日零点重置
            info.put("resetAt", LocalDate.now().plusDays(1).toString());
        }

        return info;
    }

    /**
     * 构建每日计数 Redis Key。
     */
    private String buildDailyKey(String userId) {
        return KEY_PREFIX + userId + ":" + LocalDate.now();
    }
}
