package com.xiaomizhou.dpsk.agent.builder;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.event.AgentEventType;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.*;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Workflow 模式 Builder（预留接口，暂不实现）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
public class WorkflowBuilder implements AgentBuilder {

    private final AgentDefProvider agentDefProvider;

    private final AgentComponentFactory factory;

    public WorkflowBuilder(AgentDefProvider agentDefProvider, AgentComponentFactory factory) {
        this.agentDefProvider = agentDefProvider;
        this.factory = factory;
    }

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_WORKFLOW;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {
        AgentDef agentDef = agentDefProvider.getByCode(spec.getTargetAgentCode());
        return new WorkflowAgentPipeline(init(spec),agentDef);
    }

    public static class WorkflowAgentPipeline implements AgentPipeline {

        private final UntypedAgent agent;

        private final AgentDef agentDef;

        public WorkflowAgentPipeline(UntypedAgent agent, AgentDef agentDef) {
            this.agent = agent;
            this.agentDef = agentDef;
        }

        @Override
        public PipelineResult execute(AgentCallback callback) {
            try {
                TokenStream stream = (TokenStream) agent.invoke(Map.of());
                TokenUsage[] tokenHolder = new TokenUsage[1];
                String[] contentHolder = new String[1];

                AtomicInteger index = new AtomicInteger(0);
                AtomicBoolean firstPartialMsg = new AtomicBoolean(true);
                // 标记流式过程是否出错：出错时 outputText 为空，若不单独标记会被上层误判为执行成功
                AtomicBoolean failed = new AtomicBoolean(false);
                CountDownLatch latch = new CountDownLatch(1);
                String[] errorHolder = new String[1];

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
                    // 模型侧错误（model_not_found / 鉴权 / 限流 / 连接中断）必须回推前端
                    log.error("SinglePipeline stream error for agent={}", agentDef.getCode(), error);
                    errorHolder[0] = error != null ? error.getMessage() : null;
                    failed.set(true);
                    try {
                        // langchain4j 在 ignoringExceptions 中执行本回调，抛出的异常会被静默吞掉，
                        // 因此 callback.onError 必须包住、latch.countDown() 必须放 finally。
                        // 错误只通过 onError 下发一次，不要再调用 onEvent(AgentEvent.error(...))。
                        callback.onError(error);
                    } catch (Exception callbackError) {
                        log.error("callback.onError failed for agent={}", agentDef.getCode(), callbackError);
                    } finally {
                        latch.countDown();
                    }
                }).beforeToolExecution(handle -> {
                    // 检查取消
                    if (callback.isCancelled()) {
                        return;
                    }
                    log.debug("before tool execution: {}", handle.request().name());
                    callback.onEvent(new AgentEvent(AgentEventType.TOOL_CALL, agentDef.getCode(), handle.request().id(), handle.request().name(), handle.request().arguments(), null, null));
                }).onPartialToolCallWithContext((toolCall,context) -> {

                    // 检查取消
                    if (callback.isCancelled()) {
                        if (context.streamingHandle().isCancelled()) {
                            return;
                        }
                        context.streamingHandle().cancel();
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
                        // 取消场景同样需要释放 latch，否则调用线程会永久阻塞
                        latch.countDown();
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
                    latch.countDown();
                });

                stream.start();

                latch.await();
                boolean cancelled = callback.isCancelled();
                return PipelineResult.builder()
                        // 出错（failed）必须视为失败，避免模型报错被当成成功
                        .success(!cancelled && !failed.get())
                        .outputText(contentHolder[0])
                        .errorMessage(errorHolder[0])
                        .tokenUsage(tokenHolder[0])
                        .build();

            } catch (Exception e) {
                log.error("SinglePipeline execution failed for agent={}", agentDef.getCode(), e);
                // 只通过 onError 下发一次，避免前端收到重复的 ERROR 事件
                try {
                    callback.onError(e);
                } catch (Exception callbackError) {
                    log.error("callback.onError failed for agent={}", agentDef.getCode(), callbackError);
                }
                return PipelineResult.builder()
                        .success(false)
                        .outputText(null)
                        .errorMessage(e.getMessage())
                        .build();
            }
        }
    }



    private UntypedAgent init(AgentBuildSpec spec){
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

        // 4. 获取工具
        List<ToolProvider> toolProviders = factory.getToolProviders(targetAgentCode, spec.getUserCode(), spec.getConversationCode(),spec.getMcpCodes());


        // 6. 构建 AgenticServices Agent
        return AgenticServices.agentBuilder()
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
                        return ToolErrorHandlerResult.text("工具参数错误. e:" + error.getMessage());
                    }
                })

                // 幻觉情况 => 从上下文的信息找工具执行，但工具已经不再工具列表
                .hallucinatedToolNameStrategy(factory.getToolExecutionResultMessageFunction())
                .chatMemoryProvider(memoryId -> {
                    return factory.createChatMemory(spec);
                })
                .returnType(TokenStream.class)
                .maxToolCallingRoundTrips(100)
                .build();
    }

}
