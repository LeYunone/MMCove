package com.mmcove.agent.mcp.external;

/**
 * MCP 上传跨产品线需用户确认的信号异常。
 *
 * <p>触发:目标库属于「话术指定产品线」而非「默认配置产品线」时,
 * 工具层捕获后返回询问文案(让 AI 向用户确认),用户确认后带 confirm=true 重调。
 *
 * @since 2026-09-07
 */
public class NeedConfirmException extends RuntimeException {

    public NeedConfirmException(String message) {
        super(message);
    }
}
