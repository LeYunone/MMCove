package com.mmcove.agent.core.dialog;

import com.mmcove.agent.common.model.entity.ResolvedEntity;
import com.mmcove.agent.infra.persistence.repository.ResolvedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体记忆服务(L3 实体感知记忆):跨轮保存/读取已解析实体(deviceKey/groupId),
 * 解决 v1 简单窗口导致的跨轮指代丢失(评审 ISSUE-003:「它/这台」指代错对象)。
 *
 * <p>resolveDeviceOrGroup 解析成功后调 {@link #remember} 存储;
 * loadContext 时调 {@link #getActiveEntities} 取未过期实体注入 LLM 上下文。
 *
 * @since 2026-07-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityMemoryService {

    private final ResolvedEntityRepository resolvedEntityRepository;

    /** 实体过期时间(分钟),默认 30。可通过 entity.expire-minutes 配置。 */
    @Value("${entity.expire-minutes:30}")
    private int expireMinutes;

    /**
     * 记住一个已解析实体(resolveDeviceOrGroup 解析成功后调)。
     * 同 session+displayName+type 会覆盖(保留最新解析结果)。
     */
    public void remember(String sessionId, String entityType, String entityKey,
                         String displayName, String sourceTool) {
        if (sessionId == null || entityKey == null || entityKey.isBlank()) {
            return;
        }
        // displayName 为空时用 entityKey 兜底,保证已解析的 deviceKey/groupId 能被记忆
        // (避免 deviceName 数据缺失或 target 为空时,跨轮指代静默失效)
        String effectiveName = (displayName == null || displayName.isBlank()) ? entityKey : displayName;
        ResolvedEntity e = new ResolvedEntity();
        e.setSessionId(sessionId);
        e.setEntityType(entityType);
        e.setEntityKey(entityKey);
        e.setDisplayName(effectiveName);
        e.setSourceTool(sourceTool);
        e.setExpiresAt(LocalDateTime.now().plusMinutes(expireMinutes));
        resolvedEntityRepository.save(e);
        log.debug("[EntityMemory] 记住: session={}, {} {} → {}",
                sessionId, entityType, effectiveName, entityKey);
    }

    /**
     * 清空会话下所有实体记忆(会话删除时调,防脏数据残留)。
     */
    public void forgetSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        resolvedEntityRepository.deleteBySession(sessionId);
    }

    /**
     * 取会话下未过期的实体(loadContext 注入 LLM 上下文用,跨轮指代匹配)。
     */
    public List<ResolvedEntity> getActiveEntities(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return resolvedEntityRepository.findActiveBySession(sessionId);
    }
}
