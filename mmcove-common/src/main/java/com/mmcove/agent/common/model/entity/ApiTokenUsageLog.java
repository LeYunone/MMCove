package com.mmcove.agent.common.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * API Token 使用记录实体，对应 api_token_usage_log 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_token_usage_log")
public class ApiTokenUsageLog extends BaseEntity<Long> {

    /** Token ID */
    private Long tokenId;

    /** Token密钥（脱敏存储） */
    private String tokenKey;

    /** 调用的模型 */
    private String model;

    /** 输入Token数 */
    private Integer inputTokens;

    /** 输出Token数 */
    private Integer outputTokens;

    /** 消耗的配额 */
    private Integer quotaUsed;

    /** 请求IP地址 */
    private String ipAddress;

    /** 请求路径 */
    private String requestPath;

    /** 请求方法 */
    private String requestMethod;

    /** 响应时间(毫秒) */
    private Integer responseTimeMs;

    /** 服务请求开始时间 */
    private LocalDateTime requestStartTime;

    /** 服务请求结束时间 */
    private LocalDateTime requestEndTime;

    /** 提交给AI的文本长度(字符数) */
    private Integer inputTextLength;

    /** AI返回的响应长度(字符数) */
    private Integer outputTextLength;

    /** 响应状态码 */
    private Integer statusCode;

    /** 错误信息 */
    private String errorMessage;

    /** 渠道ID */
    private Long channelId;

    /** 用户ID */
    private String userId;

    /** 分组 */
    private String groupName;

    /** 是否流式 */
    private Integer isStream;
}
