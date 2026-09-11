package com.mmcove.agent.service;

import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.model.entity.GroupRatioConfig;
import com.mmcove.agent.infra.persistence.repository.AiChannelRepository;
import com.mmcove.agent.infra.persistence.repository.ApiTokenRepository;
import com.mmcove.agent.infra.persistence.repository.GroupRatioConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 配额服务：预扣费、后扣费、精确计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final ModelPriceService modelPriceService;
    private final ApiTokenRepository apiTokenRepository;
    private final AiChannelRepository aiChannelRepository;
    private final GroupRatioConfigRepository groupRatioConfigRepository;

    /**
     * 预扣费：预估本次调用消耗的 Token 数，从用户配额中扣除。
     *
     * @return 预扣的 Token 数，-1 表示无限配额无需预扣，0 表示配额不足
     */
    public int preConsumeQuota(TokenAuthContext ctx, String model) {
        // 无限配额用户无需预扣
        if (ctx.isUnlimitedQuota()) {
            return -1;
        }

        int preQuota = modelPriceService.estimatePreQuota(model);
        if (preQuota <= 0) {
            return -1;
        }

        // 检查剩余配额是否足够
        Integer remainQuota = ctx.getRemainQuota();
        if (remainQuota == null || remainQuota < preQuota) {
            log.warn("[Quota] 预扣费失败，配额不足: tokenId={}, remain={}, preQuota={}",
                    ctx.getTokenId(), remainQuota, preQuota);
            return 0;
        }

        // 预扣
        boolean success = apiTokenRepository.decreaseQuota(ctx.getTokenId(), preQuota);
        if (success) {
            log.debug("[Quota] 预扣费成功: tokenId={}, preQuota={}", ctx.getTokenId(), preQuota);
            return preQuota;
        }

        log.warn("[Quota] 预扣费失败: tokenId={}", ctx.getTokenId());
        return 0;
    }

    /**
     * 后扣费：根据实际 Token 使用量精确计算并补齐。
     *
     * @param ctx           Token 认证上下文
     * @param inputTokens   输入 Token 数
     * @param outputTokens  输出 Token 数
     * @param model         模型名称
     * @param channelId     渠道ID
     * @param preConsumed   预扣的 Token 数（-1 表示无限配额）
     * @param responseTimeMs 响应时间(ms)
     * @return 实际消耗的 Token 费用
     */
    public int postConsumeQuota(TokenAuthContext ctx, int inputTokens, int outputTokens,
                                String model, Long channelId, int preConsumed, long responseTimeMs) {
        // 查询分组倍率
        double groupRatio = getGroupRatio(ctx.getGroupName());

        // 计算实际消耗
        int actualCost = modelPriceService.calculateTokenCost(model, inputTokens, outputTokens, groupRatio);

        log.info("[Quota] 后扣费: tokenId={}, model={}, input={}, output={}, groupRatio={}, actualCost={}, preConsumed={}",
                ctx.getTokenId(), model, inputTokens, outputTokens, groupRatio, actualCost, preConsumed);

        if (ctx.isUnlimitedQuota()) {
            // 无限配额用户只记录不扣费
            updateChannelQuota(channelId, actualCost);
            return actualCost;
        }

        // 补齐预扣与实际的差值
        if (preConsumed > 0) {
            int diff = actualCost - preConsumed;
            if (diff > 0) {
                // 实际消耗 > 预扣 → 多扣
                apiTokenRepository.decreaseQuota(ctx.getTokenId(), diff);
            } else if (diff < 0) {
                // 实际消耗 < 预扣 → 退还
                apiTokenRepository.increaseQuota(ctx.getTokenId(), -diff);
            }
        } else if (preConsumed == 0) {
            // 未预扣成功，直接扣除
            apiTokenRepository.decreaseQuota(ctx.getTokenId(), actualCost);
        }
        // preConsumed == -1: 无限配额无需处理

        // 更新渠道已使用配额
        updateChannelQuota(channelId, actualCost);

        // 更新渠道响应时间
        if (channelId != null && responseTimeMs > 0) {
            aiChannelRepository.updateResponseTime(channelId, (int) responseTimeMs);
        }

        return actualCost;
    }

    /**
     * 退还预扣配额（异常时调用）。
     */
    public void refundPreConsumed(TokenAuthContext ctx, int preConsumed) {
        if (preConsumed <= 0 || ctx.isUnlimitedQuota()) {
            return;
        }
        apiTokenRepository.increaseQuota(ctx.getTokenId(), preConsumed);
        log.info("[Quota] 退还预扣: tokenId={}, refund={}", ctx.getTokenId(), preConsumed);
    }

    /**
     * 获取分组倍率。
     */
    public double getGroupRatio(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            groupName = "default";
        }
        return groupRatioConfigRepository.findByGroupName(groupName)
                .map(GroupRatioConfig::getRatio)
                .orElse(1.0);
    }

    /**
     * 更新渠道已使用配额。
     */
    private void updateChannelQuota(Long channelId, int cost) {
        if (channelId != null && cost > 0) {
            aiChannelRepository.increaseUsedQuota(channelId, cost);
        }
    }
}
