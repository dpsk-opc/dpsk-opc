package com.xiaomizhou.dpsk.memory.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.generator.FactExtractor;
import com.xiaomizhou.dpsk.memory.model.ExtractedFact;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FactExtractor 实现，使用 OpenAiChatModel 从对话中提取长期事实。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
public class FactExtractorImpl implements FactExtractor {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FactExtractorImpl() {
        this.chatModel = OpenAiChatModel.builder()
                .modelName("deepseek-chat")
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey("sk-0803dabfa90b4a188e116e007f442a62")
                .temperature(0.1)
                .maxTokens(2048)
                .logRequests(true)
                .logResponses(true)
                .responseFormat("json_object")
                .build();
    }

    @Override
    public List<ExtractedFact> extractFacts(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        String dialogues = String.join("\n", messages);
        String prompt = MemoryConfig.L2_FACT_EXTRACTION_PROMPT_TEMPLATE
                .replace("{dialogues}", dialogues);

        try {
            String response = chatModel.chat(prompt);
            log.debug("Fact extraction response: {}", response);

            if (response == null || response.trim().isEmpty()) {
                return List.of();
            }

            return parseFacts(response);
        } catch (Exception e) {
            log.error("Failed to extract facts", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ExtractedFact> parseFacts(String json) {
        try {
            // 尝试解析 JSON 数组
            String trimmed = json.trim();
            // 可能被包裹在 {"facts": [...]} 中
            if (trimmed.startsWith("{")) {
                Map<String, Object> map = objectMapper.readValue(trimmed,
                        new TypeReference<Map<String, Object>>() {});
                if (map.containsKey("facts")) {
                    List<Map<String, Object>> factsList = (List<Map<String, Object>>) map.get("facts");
                    return factsList.stream()
                            .map(this::mapToFact)
                            .collect(java.util.stream.Collectors.toList());
                }
            }
            // 直接解析为 JSON 数组
            if (trimmed.startsWith("[")) {
                List<Map<String, Object>> factsList = objectMapper.readValue(trimmed,
                        new TypeReference<List<Map<String, Object>>>() {});
                return factsList.stream()
                        .map(this::mapToFact)
                        .collect(java.util.stream.Collectors.toList());
            }

            log.warn("Unexpected fact extraction response format: {}", trimmed);
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse facts JSON: {}", json, e);
            return List.of();
        }
    }

    private ExtractedFact mapToFact(Map<String, Object> map) {
        ExtractedFact fact = new ExtractedFact();
        fact.setType(getString(map, "type", "EVENT"));
        fact.setContent(getString(map, "content", ""));
        fact.setImportance(getDouble(map, "importance", 0.5));
        return fact;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
