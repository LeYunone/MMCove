package com.mmcove.agent.mcp.support;

import com.mmcove.agent.mcp.confirm.DangerActionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 高危操作二次确认 helper(供所有域的 MCP 工具复用)。
 *
 * <p>抽取自 {@code LmmDeviceControlTools.dangerConfirm} 的确认契约:
 * {@code @DangerousOperation} 标注的工具首次调用 → 本 helper 存执行回调到 {@link DangerActionStore}
 * (一次性令牌,TTL 300s)→ 返回 ui_render 确认契约 JSON → ObservableToolCallback 发 UI_RENDER 事件 →
 * 前端注入红色 danger-confirm-action 按钮 → 用户点确认 → POST /api/ai/danger-confirm 执行回调。</p>
 *
 * <p>每加一个高危操作只在自己的 @Tool 里调 {@link #dangerConfirm},确认链路通用不变。</p>
 *
 * @since 2026-07-21
 */
@Component
public class DangerousActionHelper {

    @Autowired
    private DangerActionStore dangerActionStore;

    /**
     * 通用危险确认:存执行回调 + 返回 ui_render 确认契约 JSON。
     *
     * @param summary    操作摘要(展示给用户)
     * @param actionType 操作类型(前端据此映射按钮文案,如 deleteUser/resetPassword)
     * @param target     操作目标(如 userId/orgId,展示/日志用)
     * @param executor   真正执行回调(确认时调用,返回工具风格 JSON)
     */
    public String dangerConfirm(String summary, String actionType, String target, Supplier<String> executor) {
        String token = dangerActionStore.store(summary, actionType, target, executor);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("confirmToken", token);
        props.put("summary", summary);
        props.put("actionType", actionType);
        props.put("target", target);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("_uiRenderEvent", true);
        resp.put("component", "danger-confirm-action");
        resp.put("props", McpToolSupport.toJson(props));
        resp.put("target", "inline");
        resp.put("pendingConfirmation", true);
        resp.put("message", "已生成二次确认按钮,请提示用户在回复下方点击确认后再执行 " + actionType);
        return McpToolSupport.toJson(resp);
    }
}
