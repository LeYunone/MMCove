package com.mmcove.agent.tools.external.annotation;

/**
 * 工具风险级别（L6 四级分级，评审 ISSUE-005）。
 * <p>取代 v1 笼统的「高危 = 一律确认」，按操作可逆性分级，减少确认疲劳：
 * <ul>
 *   <li>{@link #READ} —— 只读，无副作用（查询/截图），直接执行。</li>
 *   <li>{@link #REVERSIBLE} —— 可逆写，可恢复/可再设置（重启/关机/音量/语言/壁纸），直接执行。</li>
 *   <li>{@link #DESTRUCTIVE} —— 不可逆破坏（删除设备/出厂重置/清空数据），必须用户确认。</li>
 *   <li>{@link #PRIVILEGED} —— 特权/批量/越权（批量下发/系统级），双重确认 + 审计。</li>
 * </ul>
 */
public enum RiskLevel {
    /** 只读，无副作用：查询/截图/获取状态。直接执行，不追加确认提示。 */
    READ,
    /** 可逆写，可恢复/可再设置：重启/关机/音量/语言/壁纸。直接执行，不追加确认提示。 */
    REVERSIBLE,
    /** 不可逆破坏：删除设备/出厂重置/清空数据。追加确认提示，走用户确认流程。 */
    DESTRUCTIVE,
    /** 特权/批量/越权：批量下发/系统级操作。追加确认提示，双重确认 + 审计。 */
    PRIVILEGED
}
