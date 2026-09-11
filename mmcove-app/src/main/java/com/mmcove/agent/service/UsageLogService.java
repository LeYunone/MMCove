package com.mmcove.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mmcove.agent.common.model.entity.ApiTokenUsageLog;
import com.mmcove.agent.infra.persistence.mapper.ApiTokenUsageLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

/**
 * 用量日志服务：异步写入使用日志，提供统计查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageLogService {

    private final ApiTokenUsageLogMapper usageLogMapper;

    /**
     * 异步写入使用日志。
     */
    @Async
    public void logUsage(ApiTokenUsageLog entry) {
        try {
            usageLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("[UsageLog] 写入日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步写入使用日志（用于需要立即写入的场景）。
     */
    public void logUsageSync(ApiTokenUsageLog entry) {
        usageLogMapper.insert(entry);
    }

    /**
     * 按 Token ID 查询使用日志。
     */
    public List<ApiTokenUsageLog> getUsageByToken(Long tokenId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<ApiTokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiTokenUsageLog::getTokenId, tokenId);
        if (start != null) {
            wrapper.ge(ApiTokenUsageLog::getCreatedAt, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le(ApiTokenUsageLog::getCreatedAt, end.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(ApiTokenUsageLog::getCreatedAt);
        return usageLogMapper.selectList(wrapper);
    }

    /**
     * 按用户ID查询使用日志。
     */
    public List<ApiTokenUsageLog> getUsageByUser(String userId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<ApiTokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiTokenUsageLog::getUserId, userId);
        if (start != null) {
            wrapper.ge(ApiTokenUsageLog::getCreatedAt, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le(ApiTokenUsageLog::getCreatedAt, end.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(ApiTokenUsageLog::getCreatedAt);
        return usageLogMapper.selectList(wrapper);
    }

    /**
     * 按模型查询使用日志。
     */
    public List<ApiTokenUsageLog> getUsageByModel(String model, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<ApiTokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiTokenUsageLog::getModel, model);
        if (start != null) {
            wrapper.ge(ApiTokenUsageLog::getCreatedAt, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le(ApiTokenUsageLog::getCreatedAt, end.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(ApiTokenUsageLog::getCreatedAt);
        return usageLogMapper.selectList(wrapper);
    }

    /**
     * 按渠道ID查询使用日志。
     */
    public List<ApiTokenUsageLog> getUsageByChannel(Long channelId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<ApiTokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiTokenUsageLog::getChannelId, channelId);
        if (start != null) {
            wrapper.ge(ApiTokenUsageLog::getCreatedAt, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le(ApiTokenUsageLog::getCreatedAt, end.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(ApiTokenUsageLog::getCreatedAt);
        return usageLogMapper.selectList(wrapper);
    }

    /**
     * 明细分页查询：支持按 tokenId、model、时间范围筛选，按创建时间倒序。
     * 返回 { list, total, page, size }。
     */
    public Map<String, Object> getUsageLogs(Long tokenId, String model,
                                            LocalDate start, LocalDate end,
                                            int page, int size) {
        // 参数保护
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<ApiTokenUsageLog> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<ApiTokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        if (tokenId != null) {
            wrapper.eq(ApiTokenUsageLog::getTokenId, tokenId);
        }
        if (StringUtils.hasText(model)) {
            wrapper.eq(ApiTokenUsageLog::getModel, model);
        }
        if (start != null) {
            wrapper.ge(ApiTokenUsageLog::getCreatedAt, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le(ApiTokenUsageLog::getCreatedAt, end.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(ApiTokenUsageLog::getCreatedAt);

        IPage<ApiTokenUsageLog> result = usageLogMapper.selectPage(pageParam, wrapper);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("list", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    // ==================== 聚合统计方法 ====================

    /**
     * 概览统计：全量汇总。
     * 返回 totalTokens, totalRequests, activeTokens(最近30天有记录的Token数),
     * todayTokens, todayRequests。
     *
     * 注意：totalQuotaUsed 和 totalQuotaRemain 需要由 Controller 层从 ApiToken 表聚合后合并。
     * 此方法仅负责 api_token_usage_log 表的统计。
     */
    public Map<String, Object> getOverviewStats() {
        Map<String, Object> result = new HashMap<>();

        // 全量统计：总 Token 数、总请求数
        QueryWrapper<ApiTokenUsageLog> totalWrapper = new QueryWrapper<>();
        totalWrapper.select(
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS total_tokens",
                "COUNT(*) AS total_requests"
        );
        List<Map<String, Object>> totalResult = usageLogMapper.selectMaps(totalWrapper);
        if (totalResult != null && !totalResult.isEmpty()) {
            Map<String, Object> row = totalResult.get(0);
            result.put("totalTokens", row.get("total_tokens"));
            result.put("totalRequests", row.get("total_requests"));
        } else {
            result.put("totalTokens", 0);
            result.put("totalRequests", 0);
        }

        // 最近30天活跃 Token 数（DISTINCT token_id）
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay();
        QueryWrapper<ApiTokenUsageLog> activeWrapper = new QueryWrapper<>();
        activeWrapper.select("COUNT(DISTINCT token_id) AS active_tokens")
                .ge("created_at", thirtyDaysAgo);
        List<Map<String, Object>> activeResult = usageLogMapper.selectMaps(activeWrapper);
        if (activeResult != null && !activeResult.isEmpty()) {
            result.put("activeTokens", activeResult.get(0).get("active_tokens"));
        } else {
            result.put("activeTokens", 0);
        }

        // 今日统计
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        QueryWrapper<ApiTokenUsageLog> todayWrapper = new QueryWrapper<>();
        todayWrapper.select(
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS today_tokens",
                "COUNT(*) AS today_requests"
        ).ge("created_at", todayStart);
        List<Map<String, Object>> todayResult = usageLogMapper.selectMaps(todayWrapper);
        if (todayResult != null && !todayResult.isEmpty()) {
            Map<String, Object> row = todayResult.get(0);
            result.put("todayTokens", row.get("today_tokens"));
            result.put("todayRequests", row.get("today_requests"));
        } else {
            result.put("todayTokens", 0);
            result.put("todayRequests", 0);
        }

        return result;
    }

    /**
     * 按日统计（最近 N 天），每天返回 date, tokens, requests。
     */
    public List<Map<String, Object>> getDailyStats(int days) {
        LocalDateTime startDate = LocalDate.now().minusDays(days - 1).atStartOfDay();
        QueryWrapper<ApiTokenUsageLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "DATE(created_at) AS stat_date",
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS tokens",
                "COUNT(*) AS requests"
        ).ge("created_at", startDate)
         .groupBy("DATE(created_at)")
         .orderByAsc("DATE(created_at)");
        return usageLogMapper.selectMaps(wrapper);
    }

    /**
     * 按模型统计：每个模型的 tokens、requests、quotaUsed。
     */
    public List<Map<String, Object>> getModelStats(LocalDate start, LocalDate end) {
        QueryWrapper<ApiTokenUsageLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "model",
                "COALESCE(SUM(input_tokens), 0) AS input_tokens",
                "COALESCE(SUM(output_tokens), 0) AS output_tokens",
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS total_tokens",
                "COUNT(*) AS requests",
                "COALESCE(SUM(quota_used), 0) AS quota_used"
        );
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le("created_at", end.atTime(LocalTime.MAX));
        }
        wrapper.groupBy("model")
              .orderByDesc("total_tokens");
        return usageLogMapper.selectMaps(wrapper);
    }

    /**
     * 按 Token 统计（Top N）：每个 Token 的 tokens、requests、quotaUsed。
     */
    public List<Map<String, Object>> getTokenStats(LocalDate start, LocalDate end, int limit) {
        QueryWrapper<ApiTokenUsageLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "token_id",
                "token_key",
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS total_tokens",
                "COUNT(*) AS requests",
                "COALESCE(SUM(quota_used), 0) AS quota_used"
        );
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le("created_at", end.atTime(LocalTime.MAX));
        }
        wrapper.groupBy("token_id", "token_key")
              .orderByDesc("total_tokens")
              .last("LIMIT " + Math.max(limit, 1));
        return usageLogMapper.selectMaps(wrapper);
    }

    /**
     * 按渠道统计：每个渠道的 tokens、requests、quotaUsed。
     */
    public List<Map<String, Object>> getChannelStats(LocalDate start, LocalDate end) {
        QueryWrapper<ApiTokenUsageLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "channel_id",
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS total_tokens",
                "COUNT(*) AS requests",
                "COALESCE(SUM(quota_used), 0) AS quota_used"
        );
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le("created_at", end.atTime(LocalTime.MAX));
        }
        wrapper.groupBy("channel_id")
              .orderByDesc("total_tokens");
        return usageLogMapper.selectMaps(wrapper);
    }

    /**
     * 按 groupName 统计消耗。
     */
    public List<Map<String, Object>> getGroupStats(LocalDate start, LocalDate end) {
        QueryWrapper<ApiTokenUsageLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "group_name",
                "COALESCE(SUM(input_tokens), 0) AS input_tokens",
                "COALESCE(SUM(output_tokens), 0) AS output_tokens",
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS total_tokens",
                "COUNT(*) AS requests",
                "COALESCE(SUM(quota_used), 0) AS quota_used"
        );
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        if (end != null) {
            wrapper.le("created_at", end.atTime(LocalTime.MAX));
        }
        wrapper.groupBy("group_name")
              .orderByDesc("total_tokens");
        return usageLogMapper.selectMaps(wrapper);
    }

    /**
     * 单个 Token 的聚合统计：总请求数、成功率、平均响应时间、最后使用时间、Token消耗量。
     */
    public Map<String, Object> getTokenAggregatedStats(Long tokenId) {
        Map<String, Object> result = new HashMap<>();
        result.put("totalRequests", 0);
        result.put("successRate", 0);
        result.put("avgResponseMs", 0);
        result.put("lastUsedTime", null);
        result.put("totalTokens", 0);

        QueryWrapper<ApiTokenUsageLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "COUNT(*) AS total_requests",
                "COALESCE(SUM(CASE WHEN status_code >= 200 AND status_code < 300 THEN 1 ELSE 0 END), 0) AS success_count",
                "COALESCE(AVG(response_time_ms), 0) AS avg_response_ms",
                "MAX(created_at) AS last_used_time",
                "COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS total_tokens"
        ).eq("token_id", tokenId);

        List<Map<String, Object>> rows = usageLogMapper.selectMaps(wrapper);
        if (rows != null && !rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            long totalReqs = row.get("total_requests") != null ? ((Number) row.get("total_requests")).longValue() : 0;
            long successCount = row.get("success_count") != null ? ((Number) row.get("success_count")).longValue() : 0;

            result.put("totalRequests", totalReqs);
            result.put("successRate", totalReqs > 0 ? Math.round(successCount * 1000.0 / totalReqs) / 10.0 : 0);
            result.put("avgResponseMs", row.get("avg_response_ms") != null ? ((Number) row.get("avg_response_ms")).intValue() : 0);
            result.put("lastUsedTime", row.get("last_used_time"));
            result.put("totalTokens", row.get("total_tokens") != null ? ((Number) row.get("total_tokens")).longValue() : 0);
        }

        return result;
    }

    /**
     * 构建使用日志实体。
     */
    public static ApiTokenUsageLog buildLog(Long tokenId, String tokenKey, String model,
                                            int inputTokens, int outputTokens, int quotaUsed,
                                            Long channelId, String userId, String groupName,
                                            String ipAddress, String requestPath, String requestMethod,
                                            int responseTimeMs, int statusCode, String errorMessage,
                                            boolean isStream,
                                            LocalDateTime requestStartTime, LocalDateTime requestEndTime,
                                            int inputTextLength, int outputTextLength) {
        ApiTokenUsageLog log = new ApiTokenUsageLog();
        log.setTokenId(tokenId);
        log.setTokenKey(tokenKey);
        log.setModel(model);
        log.setInputTokens(inputTokens);
        log.setOutputTokens(outputTokens);
        log.setQuotaUsed(quotaUsed);
        log.setChannelId(channelId);
        log.setUserId(userId);
        log.setGroupName(groupName);
        log.setIpAddress(ipAddress);
        log.setRequestPath(requestPath);
        log.setRequestMethod(requestMethod);
        log.setResponseTimeMs(responseTimeMs);
        log.setRequestStartTime(requestStartTime);
        log.setRequestEndTime(requestEndTime);
        log.setInputTextLength(inputTextLength);
        log.setOutputTextLength(outputTextLength);
        log.setStatusCode(statusCode);
        log.setErrorMessage(errorMessage);
        log.setIsStream(isStream ? 1 : 0);
        return log;
    }
}
