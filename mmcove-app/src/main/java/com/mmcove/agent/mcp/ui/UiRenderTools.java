package com.mmcove.agent.mcp.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对话内 UI 渲染 MCP 工具。
 *
 * <p>当 AI 决定要在对话中指令前端响应式渲染某个业务组件时
 * (如「打开设备列表」「看 XXX 的详情/监控」「打开资源库」),
 * 通过 ReAct 自动调用本工具。本工具不做实际业务,
 * 只返回带 {@code _uiRenderEvent:true} 标记的 JSON,
 * 由 {@code ObservableToolCallback} 拦截并通过 SSE {@code ui_render} 事件推送给前端。
 *
 * <p>前端按 payload.component 在组件注册表解析后 mount,payload.props 注入属性,
 * payload.target/mode 决定挂载位置和渲染模式。
 *
 * <p>需在 {@code group_tool_config} 表给目标分组启用 {@code renderUi} 工具名,
 * {@code AgentOrchestrator#resolveGroupToolNames} 才会加载它。
 */
@Service
@RequiredArgsConstructor
public class UiRenderTools {

    private static final Logger log = LoggerFactory.getLogger(UiRenderTools.class);

    private final ObjectMapper objectMapper;

    @Tool(description = "【渲染业务组件】指令前端在对话中响应式渲染一个业务组件。"
            + "适用场景:用户说「查询我的设备」「看 XXX 的详情/监控」「打开资源库/用户管理」"
            + "等需要前端展示特定业务界面的意图。"
            + "调用成功后前端会自动渲染对应组件,你只需用自然语言简要说明已展示的内容即可。"
            + "component 必填(参考系统提示词中的「可渲染组件清单」);"
            + "props 为组件属性 JSON 字符串,如 {\"deviceId\":\"xxx\",\"scope\":\"mine\"};"
            + "target 可选(panel=右侧面板默认 / inline / modal);"
            + "mode 可选(stack=堆叠默认 / replace / tab)。"
            + "如果用户意图不明确或组件不在清单,不要调用本工具,改为用文字追问。")
    public String renderUi(
            @ToolParam(description = "组件标识,必填(参考系统提示词中的可渲染组件清单),如 device-list / device-detail / device-monitor / resource-lib / user-list") String component,
            @ToolParam(description = "组件属性 JSON 字符串,可选,如 {\"deviceKey\":\"LMM_001\",\"scope\":\"mine\"}") String props,
            @ToolParam(description = "渲染目标:panel=右侧面板(默认) / inline / modal", required = false) String target,
            @ToolParam(description = "渲染模式:stack=堆叠(默认) / replace / tab", required = false) String mode,
            @ToolParam(description = "展示标题,可选", required = false) String title,
            @ToolParam(description = "调用本工具的简短理由(给前端辅助展示),可选", required = false) String reason) {
        if (component == null || component.trim().isEmpty()) {
            return err("component 不能为空");
        }
        log.info("[UiRenderTools] 渲染组件: component={}, target={}, mode={}", component, target, mode);
        return ok(component, props, target, mode, title, reason);
    }

    // ==================== 内部辅助 ==================

    private String ok(String component, String props, String target, String mode,
                      String title, String reason) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("_uiRenderEvent", true);   // 标记位:ObservableToolCallback 据此推送 ui_render 事件
        resp.put("component", component);
        resp.put("props", isBlank(props) ? "{}" : props);
        resp.put("target", isBlank(target) ? "panel" : target);
        resp.put("mode", isBlank(mode) ? "stack" : mode);
        if (!isBlank(title)) {
            resp.put("title", title);
        }
        if (!isBlank(reason)) {
            resp.put("reason", reason);
        }
        return toJson(resp);
    }

    private String err(String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("_uiRenderEvent", false);
        resp.put("message", message);
        return toJson(resp);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"success\":false,\"_uiRenderEvent\":false,\"message\":\"JSON 序列化失败:"
                    + e.getMessage() + "\"}";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
