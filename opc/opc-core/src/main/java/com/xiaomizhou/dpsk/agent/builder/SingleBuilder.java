package com.xiaomizhou.dpsk.agent.builder;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.event.AgentEventType;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
        ChatMemory chatMemory = factory.createChatMemory(spec);

        // 4. 获取工具
        List<ToolProvider> toolProviders = factory.getToolProviders(targetAgentCode, spec.getUserCode(), spec.getConversationCode(),spec.getMcpCodes());


        // 构建 AiServices是因为AgenticServices不支持tool search. 这种方式不太行：会导致llm变笨
//        Assistant assistant = AiServices.builder(Assistant.class)
//                .streamingChatModel(model)
//                .toolProvider(toolProviders.get(0))
//                .toolSearchStrategy(new SimpleToolSearchStrategy())
//                .chatMemory(chatMemory)
//                .maxToolCallingRoundTrips(25)
//                .build();

        // 6. 构建 AgenticServices Agent
        UntypedAgent agent = AgenticServices.agentBuilder()
                .streamingChatModel(model)
                .name(agentDef.getName())
                .toolProviders(toolProviders)
                .userMessage(spec.getUserContent())
                .toolExecutionErrorHandler(new ToolExecutionErrorHandler() {
                    @Override
                    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
                        log.error("tool execution error:", error);
                        return ToolErrorHandlerResult.text("llm返回错误.");
                    }
                })
                .toolArgumentsErrorHandler(new ToolArgumentsErrorHandler() {
                    @Override
                    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
                        log.error("tool arguments error:", error);
                        return ToolErrorHandlerResult.text("llm执行错误.");
                    }
                })

                // 幻觉情况 => 从上下文的信息找工具执行，但工具已经不再工具列表
                .hallucinatedToolNameStrategy(factory.getToolExecutionResultMessageFunction())
                .chatMemory(chatMemory)
                .returnType(TokenStream.class)
                .maxToolCallingRoundTrips(25)
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

                AtomicBoolean firstPartialMsg = new AtomicBoolean(true);

                stream.onPartialThinkingWithContext((response,context) -> {
                    if (callback.isCancelled()) {
                        if (context.streamingHandle().isCancelled()) {
                            return;
                        }
                        context.streamingHandle().cancel();
                        callback.onEvent(new AgentEvent(AgentEventType.CANCELLED, agentDef.getCode(), null, null, null, null, null));
                    }

                    // 首次 thinking 发送 THINKING 事件
                    if (index.get() == 0) {
                        callback.onEvent(new AgentEvent(AgentEventType.THINKING, agentDef.getCode(), null, null, null, null, null));

                        callback.onEvent(AgentEvent.streamChunk(agentDef.getCode(), response.text()));
                        index.incrementAndGet();
                        return;
                    }
                    // 后续 thinking 作为 STREAM_CHUNK
                    callback.onEvent(AgentEvent.streamChunk(agentDef.getCode(), response.text()));

                }).onPartialResponseWithContext((response, context) -> {

                    log.debug("partial response: {}", response);

                    if (callback.isCancelled()) {
                        if (context.streamingHandle().isCancelled()) {
                            return;
                        }
                        context.streamingHandle().cancel();
                        callback.onEvent(new AgentEvent(AgentEventType.CANCELLED, agentDef.getCode(), null, null, null, null, null));

                    }

                    if (firstPartialMsg.get()) {

                        // 有些模型没有返回 thinking 信息，需要在第一个 partial response 时发送 THINKING 事件
                        if(0 == index.get()){
                            callback.onEvent(new AgentEvent(AgentEventType.THINKING, agentDef.getCode(), null, null, null, null, null));
                        }

                        // stop the think chunk end
                        callback.onEvent(AgentEvent.streamChunkEnd(agentDef.getCode()));

                        // 发送 MESSAGE 事件
                        callback.onEvent(new AgentEvent(AgentEventType.MESSAGE, agentDef.getCode(), null, null, null, null, null));

                        // 发送第一个字符
                        callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK, agentDef.getCode(), response.text(), null, null, null, null));
                        firstPartialMsg.set(false);
                        return;
                    }

                    callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK, agentDef.getCode(), response.text(), null, null, null, null));
                }).onError(error -> {
                    log.error("SinglePipeline stream error for agent={}", agentDef.getCode(), error);
//                    callback.onEvent(AgentEvent.error(agentDef.getCode(), error.getMessage()));
                }).beforeToolExecution(handle -> {
                    // 检查取消
                    if (callback.isCancelled()) {
                        return;
                    }
                    log.debug("before tool execution: {}", handle.request().name());
                    callback.onEvent(new AgentEvent(AgentEventType.TOOL_CALL, agentDef.getCode(), handle.request().id(), handle.request().name(), handle.request().arguments(), null, null));
                }).onPartialToolCall(toolCall -> {
                    // 检查取消
                    if (callback.isCancelled()) {
                        return;
                    }
                    log.debug("onPartialToolCall: {}", toolCall);
//                    callback.onEvent(new AgentEvent(AgentEventType.TOOL_CALL, agentDef.getCode(), toolCall., toolCall.name(), toolCall.arguments(), null, null));
                }).onToolExecuted(toolExecution -> {
                    // 检查取消
                    if (callback.isCancelled()) {
                        return;
                    }
                    log.debug("onToolExecuted: {}", toolExecution.resultContents());
                    Map<String,Object> meta = Maps.newHashMap();
                    meta.put("startTime", toolExecution.startTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    meta.put("finishTime", toolExecution.finishTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    callback.onEvent(new AgentEvent(AgentEventType.TOOL_RESULT, agentDef.getCode(), toolExecution.request().id(), toolExecution.request().name(), toolExecution.request().arguments(), toolExecution.result(), meta));
                }).onCompleteResponse(response -> {
                    // 如果已取消，不发送完成事件
                    if (callback.isCancelled()) {
                        log.info("SinglePipeline cancelled, skip complete response for agent={}", agentDef.getCode());
                        return;
                    }

                    callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK_END, agentDef.getCode(), null, null, null, null, null));

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

                boolean cancelled = callback.isCancelled();
                return PipelineResult.builder()
                        .success(!cancelled)
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

    interface Assistant {
        TokenStream chat(String message);
    }
}
