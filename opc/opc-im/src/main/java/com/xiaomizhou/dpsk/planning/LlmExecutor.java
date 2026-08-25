package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.db.TokenUsageComponent;
import com.xiaomizhou.dpsk.db.dto.UsageRecord;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用 LLM 执行封装：统一调用模型并把 token 用量写入用量表，
 * 解决规划器 / 意图分类器等零散调用未计入监控的问题。
 *
 * <p>用法：先 build 一个带关联信息的 {@link UsageRecord}，再调 {@link #chat(ChatRequest, UsageRecord)}。
 * token 落库失败不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmExecutor {

    private final AgentComponentFactory agentComponentFactory;
    private final TokenUsageComponent tokenUsageComponent;

    /**
     * 调用模型并记录 token 用量。
     *
     * @param request 完整 ChatRequest（含是否带 responseFormat 由调用方决定）
     * @param usage   关联的用量记录（agentCode / conversationCode / usageType 等可选；tokenUsage 由本方法回填）
     * @return AI 回复文本
     */
    public String chat(ChatRequest request, UsageRecord usage) {
        ChatResponse response = agentComponentFactory.createChatModel().chat(request);
        AiMessage aiMessage = response.aiMessage();
        if (aiMessage == null) {
            return "";
        }
        // 回填 token 用量并落库
        UsageRecord record = usage == null ? UsageRecord.builder().build() : usage;
        record.setTokenUsage(response.tokenUsage());
        record.setModelName(agentComponentFactory.getModelName());
        tokenUsageComponent.saveUsage(record);
        return aiMessage.text();
    }
}
