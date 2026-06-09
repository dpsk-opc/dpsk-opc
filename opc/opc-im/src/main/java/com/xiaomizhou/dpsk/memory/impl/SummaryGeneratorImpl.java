package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.generator.SummaryGenerator;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SummaryGenerator 实现，使用 OpenAiChatModel 生成增量摘要。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
public class SummaryGeneratorImpl implements SummaryGenerator {

    private final OpenAiChatModel chatModel;

    public SummaryGeneratorImpl() {
        this.chatModel = OpenAiChatModel.builder()
                .modelName("deepseek-chat")
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey("sk-0803dabfa90b4a188e116e007f442a62")
                .temperature(0.3)
                .maxTokens(1024)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public String generateIncrementalSummary(String agentName, String previousSummary,
                                              List<String> evictedMessages) {
        if (evictedMessages == null || evictedMessages.isEmpty()) {
            return previousSummary != null ? previousSummary : "";
        }

        String prev = (previousSummary == null || previousSummary.isEmpty()) ? "无" : previousSummary;
        String formatted = String.join("\n", evictedMessages);

        String prompt = MemoryConfig.L1_SUMMARY_PROMPT_TEMPLATE
                .replace("{agent_name}", agentName)
                .replace("{previous_summary}", prev)
                .replace("{formatted_messages}", formatted);

        try {
            String result = chatModel.chat(prompt);
            log.debug("Generated incremental summary for agent: {}, length: {}",
                    agentName, result != null ? result.length() : 0);
            return result != null ? result.trim() : prev;
        } catch (Exception e) {
            log.error("Failed to generate summary for agent: {}", agentName, e);
            return prev;
        }
    }
}
