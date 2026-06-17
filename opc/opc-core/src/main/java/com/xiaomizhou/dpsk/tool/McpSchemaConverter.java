package com.xiaomizhou.dpsk.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * MCP JSON Schema → LangChain4j JsonObjectSchema 转换器。
 * <p>
 * MCP 的 inputSchema 格式：
 * <pre>
 * {
 *   "type": "object",
 *   "properties": { "name": { "type": "string", "description": "..." } },
 *   "required": ["name"],
 *   "additionalProperties": false,
 *   "$schema": "..."
 * }
 * </pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Slf4j
public final class McpSchemaConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpSchemaConverter() {
    }

    /**
     * 将 MCP 的 inputSchema（Map 形式）转为 LangChain4j 的 JsonObjectSchema。
     *
     * @param inputSchema MCP inputSchema，通常来自 ToolMetadata.getParametersSchema() 解析后的 Map
     * @return JsonObjectSchema，转换失败返回 null
     */
    public static JsonObjectSchema convert(Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return null;
        }
        try {
            return parseObject(inputSchema);
        } catch (Exception e) {
            log.warn("Failed to convert MCP schema: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 MCP 的 inputSchema（JSON 字符串形式）转为 LangChain4j 的 JsonObjectSchema。
     */
    public static JsonObjectSchema convert(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(schemaJson, Map.class);
            return convert(map);
        } catch (Exception e) {
            log.warn("Failed to parse MCP schema JSON: {}", e.getMessage());
            return null;
        }
    }

    // ---- 内部解析 ----

    @SuppressWarnings("unchecked")
    private static JsonObjectSchema parseObject(Map<String, Object> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        // description
        Object desc = schema.get("description");
        if (desc instanceof String s && !s.isBlank()) {
            builder.description(s);
        }

        // properties
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> propsMap) {
            for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> propSchema) {
                    JsonSchemaElement element = parseProperty((Map<String, Object>) propSchema);
                    if (element != null) {
                        builder.addProperty(name, element);
                    }
                }
            }
        }

        // required
        Object req = schema.get("required");
        if (req instanceof List<?> reqList) {
            List<String> required = new ArrayList<>();
            for (Object item : reqList) {
                if (item != null) {
                    required.add(String.valueOf(item));
                }
            }
            if (!required.isEmpty()) {
                builder.required(required);
            }
        }

        // additionalProperties
        Object addProps = schema.get("additionalProperties");
        if (addProps instanceof Boolean b) {
            builder.additionalProperties(b);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static JsonSchemaElement parseProperty(Map<String, Object> prop) {
        String type = prop.get("type") instanceof String s ? s : "string";
        String description = prop.get("description") instanceof String s ? s : null;

        return switch (type) {
            case "string" -> {
                // 有 enum 的 string 转为 enum schema
                Object enumValues = prop.get("enum");
                if (enumValues instanceof List<?> list && !list.isEmpty()) {
                    List<String> values = list.stream().map(String::valueOf).toList();
                    yield description != null
                            ? JsonEnumSchema.builder().enumValues(values).description(description).build()
                            : JsonEnumSchema.builder().enumValues(values).build();
                }
                yield description != null
                        ? JsonStringSchema.builder().description(description).build()
                        : new JsonStringSchema();
            }
            case "integer" -> description != null
                    ? JsonIntegerSchema.builder().description(description).build()
                    : new JsonIntegerSchema();
            case "number" -> description != null
                    ? JsonNumberSchema.builder().description(description).build()
                    : new JsonNumberSchema();
            case "boolean" -> description != null
                    ? JsonBooleanSchema.builder().description(description).build()
                    : new JsonBooleanSchema();
            case "array" -> {
                Object items = prop.get("items");
                if (items instanceof Map<?, ?> itemsMap) {
                    JsonSchemaElement itemSchema = parseProperty((Map<String, Object>) itemsMap);
                    yield description != null
                            ? JsonArraySchema.builder().items(itemSchema).description(description).build()
                            : JsonArraySchema.builder().items(itemSchema).build();
                }
                yield description != null
                        ? JsonArraySchema.builder().description(description).build()
                        : JsonArraySchema.builder().build();
            }
            case "object" -> parseObject(prop); // 嵌套对象
            default -> description != null
                    ? JsonStringSchema.builder().description(description).build()
                    : new JsonStringSchema();
        };
    }
}
