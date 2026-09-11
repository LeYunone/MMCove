package com.mmcove.agent.tools.external.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 危险操作标记注解
 * <p>
 * 标记在 @Tool 方法上，声明这是一个需要分级处理的操作（L6 四级分级，评审 ISSUE-005）。
 * ToolRegistry 扫描到此注解后，按 {@link #level()} 分级处理：
 * <ul>
 *   <li>{@link RiskLevel#READ}/{@link RiskLevel#REVERSIBLE}：直接执行，不追加确认提示（减少确认疲劳）。</li>
 *   <li>{@link RiskLevel#DESTRUCTIVE}/{@link RiskLevel#PRIVILEGED}：自动在 @Tool description 后追加确认提示。</li>
 * </ul>
 * 确认流程由 AbstractExternalMcpTools 的 executeDangerous() 方法处理。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DangerousOperation {

    /**
     * 操作摘要，用于向用户展示即将执行的操作
     */
    String summary() default "";

    /**
     * 风险级别（L6 四级分级），默认 {@link RiskLevel#DESTRUCTIVE}（向后兼容：
     * 现有标注 @DangerousOperation 的工具默认仍需确认，行为不变）。
     */
    RiskLevel level() default RiskLevel.DESTRUCTIVE;
}
