package com.xiaomizhou.dpsk.agent.builder;

import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单聊模式 Builder。
 * 构建单个 Agent 的流式对话 Pipeline。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
@RequiredArgsConstructor
public class SingleBuilder implements AgentBuilder {

    private final AgentDefProvider agentDefProvider;
    private final AgentComponentFactory factory;

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_SINGLE;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {
        String targetAgentCode = spec.getTargetAgentCode();
        if (targetAgentCode == null || targetAgentCode.isEmpty()) {
            throw new IllegalArgumentException("SingleBuilder requires targetAgentCode");
        }

        // 1. 查询 Agent 定义
        AgentDef agentDef = agentDefProvider.getByCode(targetAgentCode);
        if (agentDef == null) {
            throw new IllegalArgumentException("Agent not found: " + targetAgentCode);
        }

        // 2. 构建 LLM 流式模型（优先使用 Agent 自定义配置）
        OpenAiStreamingChatModel model = factory.createStreamingModel(agentDef.getLlmConfig());

        // 3. 构建 ChatMemory
        ChatMemory chatMemory = factory.createChatMemory(
                spec.getConversationCode(), targetAgentCode, null);

        // 4. 获取工具
        List<ToolProvider> toolProviders = factory.getToolProviders(targetAgentCode, spec.getUserCode(), spec.getConversationCode());

        // 5. 组装 System Prompt
        com.xiaomizhou.dpsk.memory.assembler.ContextAssembler.AssembledPrompt enrichedPrompt = factory.assembleSystemPrompt(
                agentDef, spec.getUserContent(), spec.getUserCode(),
                spec.getConversationCode(), spec.getQuoteMessageCode());

        // 5.1 注入定时任务锚点上下文（如果有）
        String systemMessage = enrichedPrompt.getSystemPart();
        if (spec.getTaskContext() != null && !spec.getTaskContext().isEmpty()) {
            systemMessage = systemMessage + "\n\n" + spec.getTaskContext();
        }

        // 6. 构建 AgenticServices Agent
        UntypedAgent agent = AgenticServices.agentBuilder()
                .streamingChatModel(model)
                .name(agentDef.getName())
                .systemMessage(systemMessage)
                .toolProviders(toolProviders)
                .userMessage(spec.getUserContent())
                .chatMemory(chatMemory)
                .returnType(TokenStream.class)
                .maxToolCallingRoundTrips(10)
                .build();

        return new SinglePipeline(agent, agentDef);
    }

    /**
     * 单聊 Pipeline 实现。
     */
    @Slf4j
    private static class SinglePipeline implements AgentPipeline {

        private final UntypedAgent agent;
        private final AgentDef agentDef;

        SinglePipeline(UntypedAgent agent, AgentDef agentDef) {
            this.agent = agent;
            this.agentDef = agentDef;
        }

        @Override
        public PipelineResult execute(AgentCallback callback) {
            try {
                TokenStream stream = (TokenStream) invokeAgent();
                TokenUsage[] tokenHolder = new TokenUsage[1];
                String[] contentHolder = new String[1];

                AtomicInteger index = new AtomicInteger(0);

                stream.onPartialThinking(response -> {
                    // 首次 thinking 发送 THINKING 事件
                    if (index.get() == 0) {
                        callback.onEvent(AgentEvent.thinking(agentDef.getCode(), response.text()));
                    }
                    // 后续 thinking 作为 STREAM_CHUNK
                    callback.onEvent(AgentEvent.streamChunk(agentDef.getCode(), response.text()));
                    index.incrementAndGet();

                }).onError(error -> {
                    log.error("SinglePipeline stream error for agent={}", agentDef.getCode(), error);
                    callback.onEvent(AgentEvent.error(agentDef.getCode(), error.getMessage()));
                    callback.onError(error);

                }).onCompleteResponse(response -> {
                    String content = response.aiMessage().text();
                    contentHolder[0] = content;
                    tokenHolder[0] = response.tokenUsage();

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("content", content);
                    if (response.tokenUsage() != null) {
                        meta.put("tokenUsage", response.tokenUsage());
                    }
                    callback.onEvent(AgentEvent.done(agentDef.getCode(), meta));
                    callback.onComplete();
                });

                stream.start();

                return PipelineResult.builder()
                        .success(true)
                        .outputText(contentHolder[0])
                        .tokenUsage(tokenHolder[0])
                        .build();

            } catch (Exception e) {
                log.error("SinglePipeline execution failed for agent={}", agentDef.getCode(), e);
                callback.onEvent(AgentEvent.error(agentDef.getCode(), e.getMessage()));
                callback.onError(e);
                return PipelineResult.builder()
                        .success(false)
                        .outputText(null)
                        .build();
            }
        }

        private Object invokeAgent() {
            try {
                return agent.invoke(Map.of());
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke AgenticServices agent", e);
            }
        }
    }
}
