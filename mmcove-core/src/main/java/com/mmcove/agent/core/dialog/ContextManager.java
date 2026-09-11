package com.mmcove.agent.core.dialog;

import org.springframework.stereotype.Component;

/**
 * 上下文引用解析器，管理对话中的上下文引用。
 */
@Component
public class ContextManager {

    /**
     * 解析消息中的上下文引用（如 @file、@web 引用）。
     * 预留接口，用于未来的上下文扩展。
     */
    public String resolveReferences(String message) {
        // 未来：解析 @file、@web、@tool 引用
        return message;
    }
}
