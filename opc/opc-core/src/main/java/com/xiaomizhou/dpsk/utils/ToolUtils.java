package com.xiaomizhou.dpsk.utils;

import com.xiaomizhou.dpsk.tool.model.ToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ToolUtils {

    private ToolUtils() {
    }


    /**
     * 将 LC4j 的 {@link ToolExecutionRequest} 转换为内部的 {@link ToolCall}。
     */
    public static ToolCall toToolCall(ToolExecutionRequest request) {
        Map<String, Object> params = new HashMap<>();

        // 尝试将 LC4j 的 arguments（通常是 JSON String）解析为 Map
        String arguments = request.arguments();
        if (arguments != null && !arguments.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(arguments, Map.class);
                params.putAll(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse tool arguments JSON for '{}': {}. Using raw string.",
                        request.name(), e.getMessage());
                params.put("_rawArguments", arguments);
            }
        }

        return ToolCall.builder()
                .name(request.name())
                .callId(request.id())
                .parameters(params)
                .build();
    }
}
