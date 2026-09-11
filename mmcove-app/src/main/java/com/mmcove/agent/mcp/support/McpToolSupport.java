package com.mmcove.agent.mcp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具通用返回/解析助手。
 *
 * <p>抽取自 {@code LmmDeviceControlTools} 的与领域无关的方法(ok/err/toJson/isBlank/toInt 等),
 * 供所有域的 MCP 工具类(UserOrg/Content/File/IotDevice/Analytics)统一复用,保证:
 * <ul>
 *   <li>返回 JSON 结构一致({success,desc,data} / {success,message} / developing 占位)</li>
 *   <li>日志风格统一(RPC 失败统一 warn)</li>
 *   <li>参数解析容错一致(String→数值,空串/null 安全)</li>
 * </ul>
 *
 * <p>纯静态工具类,无状态,所有方法线程安全。设备域特化的 dispatch/RpcCall(与 ControlResultVO 耦合)
 * 不在此抽取,仍保留在 LmmDeviceControlTools 内。
 *
 * @since 2026-07-20
 */
public final class McpToolSupport {

    private static final Logger log = LoggerFactory.getLogger(McpToolSupport.class);

    /**
     * 共享 ObjectMapper:注册 JavaTimeModule 支持 LocalDateTime,禁用 WRITE_DATES_AS_TIMESTAMPS
     * 让日期以 ISO 字符串输出(对 LLM 更友好)。各工具类序列化 VO 时统一用本实例。
     */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private McpToolSupport() {
    }

    /**
     * 构造成功返回:{success:true, desc, data}。
     *
     * @param desc 结果描述(给 LLM 看的简要说明)
     * @param data 业务数据(可为 null/集合/对象,统一 JSON 序列化)
     */
    public static String ok(String desc, Object data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("desc", desc);
        resp.put("data", data);
        return toJson(resp);
    }

    /**
     * 构造失败返回:{success:false, message}。用于参数校验、业务前置条件不满足等非 RPC 异常。
     */
    public static String err(String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("message", message);
        return toJson(resp);
    }

    /**
     * 构造"开发中"占位返回,供后端尚未就绪的工具(报表/时序/报修/告警等硬缺口)承接意图。
     * 返回:{status:"developing", feature, message, suggestion?}。
     *
     * <p>设计意图:让 LLM 识别到该意图时,明确告知用户"该能力开发中"而非调用失败;
     * 后续后端就绪后只需把工具方法体替换为真实 RPC,工具签名不变。
     *
     * @param feature    能力标识(如 "contentReport")
     * @param message    开发中提示文案
     * @param suggestion 替代建议(可空,如 "可先用 getDeviceOverviewStats 查设备统计")
     */
    public static String developing(String feature, String message, String suggestion) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "developing");
        resp.put("feature", feature);
        resp.put("message", message);
        if (suggestion != null) {
            resp.put("suggestion", suggestion);
        }
        return toJson(resp);
    }

    /**
     * RPC 调用失败统一返回:{success:false, message, hints}。并打 warn 日志。
     *
     * @param centerName 中心名(如 "user-center"),用于拼接"调用 xxx 失败"与 hints
     * @param toolName   工具名(日志定位)
     * @param target     操作目标(如 deviceKey/userId,日志定位,可空)
     * @param e          RPC 异常
     */
    public static String rpcFail(String centerName, String toolName, String target, Exception e) {
        log.warn("[McpTool] RPC 失败: center={}, tool={}, target={}, error={}",
                centerName, toolName, target, e.getMessage());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", false);
        resp.put("message", "调用 " + centerName + " 失败: " + e.getMessage());
        List<String> hints = new ArrayList<>();
        hints.add(centerName + "-server 是否已启动并注册到 nacos");
        hints.add("对应 rpc-api / model 版本是否匹配");
        resp.put("hints", hints);
        return toJson(resp);
    }

    /**
     * 安全序列化为 JSON 字符串。序列化异常时返回兜底错误 JSON(保证工具永远返回合法 JSON 给 LLM)。
     */
    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"JSON 序列化失败:" + e.getMessage() + "\"}";
        }
    }

    /** 字符串是否空白(null/空串/全空格)。 */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 字符串是否非空白。 */
    public static boolean notBlank(String s) {
        return !isBlank(s);
    }

    /** String→Integer,空串或非数字返回 null(不抛异常,适配 @Tool 全 String 入参约定)。 */
    public static Integer toInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** String→Long,空串或非数字返回 null(不抛异常)。 */
    public static Long toLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 逗号分隔字符串 → 去空白后的非空元素列表。
     * 用于"多个 userId/deviceId/roleId"等批量入参解析。
     */
    public static List<String> splitCsv(String csv) {
        if (isBlank(csv)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String k : csv.split(",")) {
            String t = k.trim();
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }
}
