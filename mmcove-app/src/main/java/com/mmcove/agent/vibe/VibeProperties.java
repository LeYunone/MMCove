package com.mmcove.agent.vibe;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Vibe 流水线领域配置(mmcove.vibe)。
 *
 * @since 2026-09-11
 */
@Data
@Component
@ConfigurationProperties(prefix = "mmcove.vibe")
public class VibeProperties {

    /** RAG 锚点相似度门槛(Milvus COSINE,越高越相关;低于门槛的切片不注入,宁缺毋滥) */
    private double minScore = 0.55;

    /** 每个知识库的锚点召回数 */
    private int perKbTopK = 4;

    /** 多库合并后的锚点总切片数上限(约 12×800 token,控提示词体积) */
    private int knowledgeTopN = 12;

    /** LLM 意图兜底开关(false=纯关键词+默认兜底,本地调试/降级用) */
    private boolean llmIntentEnabled = true;

    /** 关键词并列且 LLM 失效时的兜底意图类型 */
    private String defaultIntentType = "FEATURE_DEV";

    /** 单个阶段产物的字符数上限(防超大报告撑爆 MCP 通道;超限拒绝并提示精简) */
    private int artifactMaxChars = 100000;

    /** 产物超限时的截断保存长度(进度照常落库,产物存截断版,不再原子失败) */
    private int artifactTruncateChars = 5000;

    /** 主提示词 UTF-8 字节预算(超限先砍低分锚点片再渲染,防快照被存储层截断) */
    private int promptMaxBytes = 60000;

    /** RUNNING 实例的闲置收割阈值(小时;低频写时清扫,防僵尸 run 污染恢复兜底) */
    private int runIdleSweepHours = 24;
}
