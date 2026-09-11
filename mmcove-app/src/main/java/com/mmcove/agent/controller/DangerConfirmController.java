package com.mmcove.agent.controller;

import com.mmcove.agent.common.model.dto.ApiResponse;
import com.mmcove.agent.mcp.confirm.DangerActionStore;
import com.mmcove.agent.mcp.confirm.DangerActionStore.PendingDanger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 危险操作确认控制器(P2-C)。
 *
 * <p>前端"确认按钮"点击后调用:凭一次性令牌取出预存的执行回调,执行真正的高危 RPC
 * (删除设备/出厂重置)。不经 AI/对话,直连下发,确定且快。
 *
 * <p>链路:AI 调高危 @Tool → 工具存回调返回 ui_render 确认契约 → 前端渲染红色确认按钮 →
 * 用户点确认 → 本接口取回调执行 → 返回结果给前端 toast。
 *
 * @since 2026-07-16
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class DangerConfirmController {

    private final DangerActionStore dangerActionStore;

    @PostMapping("/danger-confirm")
    public ApiResponse<Map<String, Object>> confirm(@RequestBody DangerConfirmRequest request) {
        if (request == null || isBlank(request.confirmationToken())) {
            return ApiResponse.error(400, "确认令牌不能为空");
        }
        PendingDanger action = dangerActionStore.retrieveAndRemove(request.confirmationToken());
        if (action == null) {
            return ApiResponse.error(410, "确认令牌无效或已过期(5 分钟内有效),请重新发起操作");
        }
        log.info("[DangerConfirm] 执行确认: type={}, target={}", action.actionType(), action.target());
        try {
            // 执行预存回调:跑真实 Dubbo RPC,返回工具风格 JSON({success,message,desc,data})
            String result = action.executor().get();
            // executors(doXxx)内部 catch 异常返回 {success:false,...} 而非抛错;
            // 这里必须解析结果,success=false 时把失败透传给前端,否则 controller 误返 success、
            // 前端弹"已完成"但实际 RPC 没执行(如删除分组未生效)。
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
                if (node.has("success") && !node.get("success").asBoolean()) {
                    String msg = node.hasNonNull("message") ? node.get("message").asText()
                            : (node.hasNonNull("desc") ? node.get("desc").asText() : "执行失败");
                    log.warn("[DangerConfirm] 执行返回失败: type={}, target={}, msg={}",
                            action.actionType(), action.target(), msg);
                    return ApiResponse.error(500, msg);
                }
            } catch (Exception parseEx) {
                log.debug("[DangerConfirm] 结果非 JSON,按成功处理: {}", result);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("actionType", action.actionType());
            data.put("target", action.target());
            data.put("result", result);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.warn("[DangerConfirm] 执行失败: type={}, target={}, err={}",
                    action.actionType(), action.target(), e.getMessage());
            return ApiResponse.error(500, "执行失败: " + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 危险操作确认请求。 */
    public record DangerConfirmRequest(String confirmationToken) {
    }
}
