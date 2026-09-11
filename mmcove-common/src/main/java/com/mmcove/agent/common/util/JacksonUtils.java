package com.mmcove.agent.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * JSON 工具类，封装 Jackson ObjectMapper。
 */
@Slf4j
public final class JacksonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private JacksonUtils() {
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("对象序列化为 JSON 失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化为 {} 失败: {}", clazz.getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static Map<String, Object> toMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON 解析为 Map 失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
