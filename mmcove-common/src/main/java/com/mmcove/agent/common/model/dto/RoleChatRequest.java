package com.mmcove.agent.common.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 带 roleInfo 的聊天请求 DTO，兼容 Go 项目入参。
 */
@Data
public class RoleChatRequest {

    /** 模型名称 */
    private String model;

    /** 消息列表 */
    private List<RoleChatMessage> messages;

    /** 温度 */
    private Double temperature;

    /** 最大token数 */
    private Integer maxTokens;

    /** 是否流式 */
    private Boolean stream;

    /** 角色信息（大屏场景特有） */
    private RoleInfo roleInfo;

    /** 用户问题 */
    private String userAsk;



    /**
     * 角色信息。
     */
    @Data
    public static class RoleInfo {
        /** 角色编码（如 def_translator） */
        private String code;
        /** 模板参数（替换 {{key}}） */
        private Map<String, String> args;

    }

    /**
     * 聊天消息。
     */
    @Data
    public static class RoleChatMessage {
        /** 角色：system/user/assistant */
        private String role;
        /**
         * 内容，支持两种格式：
         * - 字符串：纯文本内容
         * - List：多模态内容 [{"type":"text","text":"..."}, {"type":"image_url",...}]
         */
        private Object content;

        /** 获取文本内容（兼容 String 和 List 格式） */
        public String getContentAsString() {
            if (content == null) {
                return null;
            }
            if (content instanceof String s) {
                return s;
            }
            if (content instanceof List<?> list && !list.isEmpty()) {
                // 多模态场景，提取第一个 text 类型的内容
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
                        Object text = map.get("text");
                        return text != null ? text.toString() : null;
                    }
                }
                return list.get(0).toString();
            }
            return content.toString();
        }
    }
}
