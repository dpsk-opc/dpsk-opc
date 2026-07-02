package com.xiaomizhou.dpsk.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 11:48
 * @description
 */
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        // 推荐使用 JsonMapper 构建，支持更多配置
        OBJECT_MAPPER = JsonMapper.builder()
                // 忽略未知字段，避免反序列化时报错
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // 允许字段名不带引号（非标准，慎用，通常关闭）
                // .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, false)
                // 日期格式：ISO-8601 或自定义
                // .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // 序列化时排除 null 值
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // 支持 Java 8 时间类型
                .addModule(new JavaTimeModule())
                .build();
    }

    private JsonUtils() {
        // 私有构造器，防止实例化
    }

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);

        }
    }

    /**
     * JSON 字符串转普通 Java 对象
     */
    public static <T> T toObj(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    /**
     * JSON 字符串转泛型对象（如 List<User>, Map<String, Object> 等）
     */
    public static <T> T toObj(String json, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    /**
     * 获取当前使用的 ObjectMapper（只读，通常用于高级定制）
     * 注意：不要随意修改返回的 ObjectMapper，否则会影响全局行为
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

}
