package com.mmcove.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mmcove.agent.common.context.TokenAuthContext;
import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.common.model.dto.FluxImageRequest;
import com.mmcove.agent.common.model.dto.FluxImageResponse;
import com.mmcove.agent.common.model.entity.AiChannel;
import com.mmcove.agent.common.model.entity.ApiToken;
import com.mmcove.agent.infra.persistence.mapper.AiChannelMapper;
import com.mmcove.agent.infra.persistence.mapper.ApiTokenMapper;
import com.mmcove.agent.service.ChannelLoadBalancer;
import com.mmcove.agent.service.FluxImageService;
import com.mmcove.agent.service.UsageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大屏 AI 控制器：文生图接口 + 用量统计。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DashboardAiController {

    private final FluxImageService fluxImageService;
    private final ChannelLoadBalancer channelLoadBalancer;
    private final UsageLogService usageLogService;
    private final ApiTokenMapper apiTokenMapper;
    private final AiChannelMapper aiChannelMapper;

    /**
     * 文生图/图生图接口（兼容 Go 项目 /v1/flux-2-pro）。
     * 通过渠道路由选择上游 API，支持多渠道负载均衡和失败重试。
     */
    @PostMapping("/v1/flux-2-pro")
    public FluxImageResponse flux2Pro(@RequestBody FluxImageRequest request) {
        log.info("[FluxImage] 收到图片生成请求: prompt={}, hasInputImage={}",
                request.getPrompt(), request.getInput_image() != null);

        TokenAuthContext context = TokenAuthContext.get();
        String groupName = context.getGroupName();
        String model = request.getModel() != null ? request.getModel() : "flux.2-pro";

        // 图片分析只做一次，重试不再重复调用 LLM
        String prompt = fluxImageService.preparePrompt(request, groupName);

        int maxRetry = 3;
        Set<Long> triedChannelIds = new HashSet<>();
        for (int retry = 0; retry < maxRetry; retry++) {
            ChannelLoadBalancer.SelectedChannel selected = channelLoadBalancer.selectChannel(
                    groupName, model, retry, triedChannelIds);
            if (selected == null || selected.channel() == null) {
                log.error("[FluxImage] 无可用渠道: group={}, model={}, retry={}", groupName, model, retry);
                break;
            }

            AiChannel channel = selected.channel();
            triedChannelIds.add(channel.getId());
            log.info("[FluxImage] 使用渠道: channelId={}, name={}, retry={}", channel.getId(), channel.getName(), retry);

            FluxImageResponse response = fluxImageService.generateImage(request, channel, prompt);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                return response;
            }

            log.warn("[FluxImage] 渠道调用失败: channelId={}, code={}, retry={}/{}",
                    channel.getId(), response.getCode(), retry + 1, maxRetry);
        }

        return FluxImageResponse.error(503, "所有图片生成渠道均不可用");
    }

    // ==================== 聚合统计接口 ====================

    /**
     * 概览统计。
     * GET /api/dashboard/overview
     */
    @GetMapping("/api/dashboard/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> result = usageLogService.getOverviewStats();

        // activeChannels: 启用状态的渠道数
        QueryWrapper<AiChannel> channelWrapper = new QueryWrapper<>();
        channelWrapper.eq("status", 1);
        Long activeChannels = aiChannelMapper.selectCount(channelWrapper);
        result.put("activeChannels", activeChannels);

        // totalQuotaUsed / totalQuotaRemain: 从 api_token 表聚合
        QueryWrapper<ApiToken> tokenWrapper = new QueryWrapper<>();
        tokenWrapper.select(
                "COALESCE(SUM(used_quota), 0) AS total_quota_used",
                "COALESCE(SUM(remain_quota), 0) AS total_quota_remain"
        );
        List<Map<String, Object>> tokenStats = apiTokenMapper.selectMaps(tokenWrapper);
        if (tokenStats != null && !tokenStats.isEmpty()) {
            Map<String, Object> row = tokenStats.get(0);
            result.put("totalQuotaUsed", row.get("total_quota_used"));
            result.put("totalQuotaRemain", row.get("total_quota_remain"));
        } else {
            result.put("totalQuotaUsed", 0);
            result.put("totalQuotaRemain", 0);
        }

        return ApiResponse.success(result);
    }

    /**
     * 按日统计（最近 N 天）。
     * GET /api/dashboard/usage/daily?days=7
     */
    @GetMapping("/api/dashboard/usage/daily")
    public ApiResponse<List<Map<String, Object>>> dailyStats(
            @RequestParam(defaultValue = "7") int days) {
        if (days < 1) {
            days = 7;
        }
        if (days > 365) {
            days = 365;
        }
        return ApiResponse.success(usageLogService.getDailyStats(days));
    }

    /**
     * 按模型统计。
     * GET /api/dashboard/usage/by-model?start=2026-01-01&end=2026-05-19
     */
    @GetMapping("/api/dashboard/usage/by-model")
    public ApiResponse<List<Map<String, Object>>> statsByModel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (start == null) {
            start = LocalDate.now().minusDays(7);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        return ApiResponse.success(usageLogService.getModelStats(start, end));
    }

    /**
     * 按 Token 统计（Top N）。
     * GET /api/dashboard/usage/by-token?limit=10
     */
    @GetMapping("/api/dashboard/usage/by-token")
    public ApiResponse<List<Map<String, Object>>> statsByToken(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "10") int limit) {
        if (start == null) {
            start = LocalDate.now().minusDays(7);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        if (limit < 1) {
            limit = 10;
        }
        return ApiResponse.success(usageLogService.getTokenStats(start, end, limit));
    }

    /**
     * 按渠道统计。
     * GET /api/dashboard/usage/by-channel?start=2026-01-01&end=2026-05-19
     */
    @GetMapping("/api/dashboard/usage/by-channel")
    public ApiResponse<List<Map<String, Object>>> statsByChannel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (start == null) {
            start = LocalDate.now().minusDays(7);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        return ApiResponse.success(usageLogService.getChannelStats(start, end));
    }

    /**
     * 按分组统计消耗。
     * GET /api/dashboard/usage/by-group?start=2026-01-01&end=2026-05-19
     */
    @GetMapping("/api/dashboard/usage/by-group")
    public ApiResponse<List<Map<String, Object>>> statsByGroup(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        if (start == null) {
            start = LocalDate.now().minusDays(7);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        return ApiResponse.success(usageLogService.getGroupStats(start, end));
    }

    /**
     * 使用明细分页查询。
     * GET /api/dashboard/usage/logs?tokenId=&model=&start=&end=&page=1&size=20
     * 返回 { list, total, page, size }。
     */
    @GetMapping("/api/dashboard/usage/logs")
    public ApiResponse<Map<String, Object>> usageLogs(
            @RequestParam(required = false) Long tokenId,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (start == null) {
            start = LocalDate.now().minusDays(7);
        }
        if (end == null) {
            end = LocalDate.now();
        }
        return ApiResponse.success(usageLogService.getUsageLogs(tokenId, model, start, end, page, size));
    }

    /**
     * Token 配额使用详情。
     * GET /api/dashboard/quota-details
     */
    @GetMapping("/api/dashboard/quota-details")
    public ApiResponse<List<Map<String, Object>>> quotaDetails() {
        QueryWrapper<ApiToken> wrapper = new QueryWrapper<>();
        wrapper.select(
                "id", "name", "user_id", "group_name",
                "status", "unlimited_quota",
                "used_quota", "remain_quota",
                "CASE WHEN unlimited_quota = 1 THEN -1 ELSE used_quota + remain_quota END AS total_quota"
        ).eq("status", 1);
        List<Map<String, Object>> tokens = apiTokenMapper.selectMaps(wrapper);

        for (Map<String, Object> token : tokens) {
            Object totalQuota = token.get("total_quota");
            Object usedQuota = token.get("used_quota");
            if (totalQuota != null && usedQuota != null) {
                long total = ((Number) totalQuota).longValue();
                long used = ((Number) usedQuota).longValue();
                if (total > 0) {
                    token.put("usage_percent", Math.round(used * 100.0 / total));
                } else {
                    token.put("usage_percent", 0);
                }
            } else {
                token.put("usage_percent", 0);
            }
        }

        return ApiResponse.success(tokens);
    }
}
