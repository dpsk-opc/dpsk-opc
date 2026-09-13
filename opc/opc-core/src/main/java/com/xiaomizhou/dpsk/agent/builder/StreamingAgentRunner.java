package com.xiaomizhou.dpsk.agent.builder;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.AgentCallback;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.event.AgentEventType;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用流式 Agent 执行器。
 * <p>
 * 将 UntypedAgent（streaming model + TokenStream）的完整流式事件处理逻辑集中于此，
 * 供 {@link SingleBuilder} 与 {@link GroupBuilder} 复用，避免两份相同的流式处理逻辑漂移。
 * <p>
 * 事件序列：THINKING → (STREAM_CHUNK)* → STREAM_CHUNK_END → MESSAGE → MESSAGE_CHUNK* → MESSAGE_CHUNK_END → DONE
 * 并穿插 TOOL_CALL / TOOL_RESULT / CANCELLED / ERROR。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/2
 */
@Slf4j
public final class StreamingAgentRunner {

    private StreamingAgentRunner() {
    }

    /**
     * 执行一个流式 Agent，阻塞直到完成或取消。
     *
     * @param agent     已构建好的流式 UntypedAgent（returnType 为 TokenStream）
     * @param agentDef  Agent 定义（用于取 code 标识）
     * @param callback  事件回调（接收 thinking / tool / message chunk / done 等语义事件）
     * @return 执行结果
     */
    public static PipelineResult run(UntypedAgent agent, AgentDef agentDef, AgentCallback callback) {
        String agentCode = agentDef.getCode();
        try {
            Object invokeResult = agent.invoke(Map.of());
            if (!(invokeResult instanceof TokenStream stream)) {
                throw new IllegalStateException("Expected TokenStream but got " + invokeResult.getClass().getName());
            }

            TokenUsage[] tokenHolder = new TokenUsage[1];
            String[] contentHolder = new String[1];

            AtomicInteger index = new AtomicInteger(0);
            AtomicBoolean firstPartialMsg = new AtomicBoolean(true);
            // 标记流式过程是否出错：出错时 outputText 为空，若不单独标记会被上层误判为执行成功
            AtomicBoolean failed = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            String[] errorHolder = new String[1];

            stream.onPartialThinkingWithContext((response, context) -> {
                if (callback.isCancelled()) {
                    if (context.streamingHandle().isCancelled()) {
                        return;
                    }
                    context.streamingHandle().cancel();
                    callback.onEvent(new AgentEvent(AgentEventType.CANCELLED, agentCode, null, null, null, null, null));
                }

                // 首次 thinking 发送 THINKING 事件
                if (index.get() == 0) {
                    callback.onEvent(new AgentEvent(AgentEventType.THINKING, agentCode, null, null, null, null, null));
                    callback.onEvent(AgentEvent.streamChunk(agentCode, response.text()));
                    index.incrementAndGet();
                    return;
                }
                // 后续 thinking 作为 STREAM_CHUNK
                callback.onEvent(AgentEvent.streamChunk(agentCode, response.text()));

            }).onPartialResponseWithContext((response, context) -> {

                log.debug("partial response: {}", response);

                if (callback.isCancelled()) {
                    if (context.streamingHandle().isCancelled()) {
                        return;
                    }
                    context.streamingHandle().cancel();
                    callback.onEvent(new AgentEvent(AgentEventType.CANCELLED, agentCode, null, null, null, null, null));
                }

                if (firstPartialMsg.get()) {

                    // 有些模型没有返回 thinking 信息，需要在第一个 partial response 时发送 THINKING 事件
                    if (0 == index.get()) {
                        callback.onEvent(new AgentEvent(AgentEventType.THINKING, agentCode, null, null, null, null, null));
                    }

                    // stop the think chunk end
                    callback.onEvent(AgentEvent.streamChunkEnd(agentCode));

                    // 发送 MESSAGE 事件
                    callback.onEvent(new AgentEvent(AgentEventType.MESSAGE, agentCode, null, null, null, null, null));

                    // 发送第一个字符
                    callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK, agentCode, response.text(), null, null, null, null));
                    firstPartialMsg.set(false);
                    return;
                }

                callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK, agentCode, response.text(), null, null, null, null));
            }).onError(error -> {
                // 这类异常是模型侧错误（如 model_not_found / 鉴权失败 / 限流 / 连接中断），
                // 必须回推前端，否则用户只看到转圈、完全没有失败原因。
                log.error("StreamingAgentRunner stream error for agent={}", agentCode, error);
                errorHolder[0] = error != null ? error.getMessage() : null;
                failed.set(true);
                try {
                    // 注意 1：langchain4j 在 ServerSentEventListenerUtils.ignoringExceptions 中执行本回调，
                    //         这里抛出的任何异常都会被静默吞掉，因此 callback.onError 必须包住，
                    //         且 latch.countDown() 必须放在 finally，否则调用线程会永久阻塞。
                    // 注意 2：错误只通过 onError 下发一次（onError 携带原始 Throwable），不要再调用
                    //         onEvent(AgentEvent.error(...))，否则前端会收到两条重复的 ERROR。
                    callback.onError(error);
                } catch (Exception callbackError) {
                    log.error("callback.onError failed for agent={}", agentCode, callbackError);
                } finally {
                    latch.countDown();
                }
            }).beforeToolExecution(handle -> {
                if (callback.isCancelled()) {
                    return;
                }
                log.debug("before tool execution: {}", handle.request().name());
                callback.onEvent(new AgentEvent(AgentEventType.TOOL_CALL, agentCode, handle.request().id(), handle.request().name(), handle.request().arguments(), null, null));
            }).onPartialToolCallWithContext((toolCall, context) -> {
                if (callback.isCancelled()) {
                    if (context.streamingHandle().isCancelled()) {
                        return;
                    }
                    context.streamingHandle().cancel();
                    return;
                }
                log.debug("onPartialToolCall: {}", toolCall);
            }).onToolExecuted(toolExecution -> {
                if (callback.isCancelled()) {
                    return;
                }
                log.debug("onToolExecuted: {}", toolExecution.resultContents());
                Map<String, Object> meta = Maps.newHashMap();
                meta.put("startTime", toolExecution.startTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                meta.put("finishTime", toolExecution.finishTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                callback.onEvent(new AgentEvent(AgentEventType.TOOL_RESULT, agentCode, toolExecution.request().id(), toolExecution.request().name(), toolExecution.request().arguments(), toolExecution.result(), meta));
            }).onCompleteResponse(response -> {
                if (callback.isCancelled()) {
                    log.info("StreamingAgentRunner cancelled, skip complete response for agent={}", agentCode);
                    // 取消场景同样需要释放 latch，否则调用线程会永久阻塞
                    latch.countDown();
                    return;
                }

                callback.onEvent(new AgentEvent(AgentEventType.MESSAGE_CHUNK_END, agentCode, null, null, null, null, null));

                String content = response.aiMessage().text();
                contentHolder[0] = content;
                tokenHolder[0] = response.tokenUsage();

                Map<String, Object> meta = new HashMap<>();
                meta.put("content", content);
                if (response.tokenUsage() != null) {
                    meta.put("tokenUsage", response.tokenUsage());
                }
                callback.onEvent(AgentEvent.done(agentCode, meta));
                callback.onComplete();
                latch.countDown();
            });

            stream.start();
            latch.await();

            boolean cancelled = callback.isCancelled();
            return PipelineResult.builder()
                    // 出错（failed）必须视为失败：模型报错时 outputText 为 null，
                    // 若仍返回 success=true 会让上层把失败当成功（任务标记完成、群聊失败判定失效）
                    .success(!cancelled && !failed.get())
                    .outputText(contentHolder[0])
                    .errorMessage(errorHolder[0])
                    .tokenUsage(tokenHolder[0])
                    .build();

        } catch (Exception e) {
            log.error("StreamingAgentRunner execution failed for agent={}", agentCode, e);
            // 只通过 onError 下发一次，避免前端收到重复的 ERROR 事件
            try {
                callback.onError(e);
            } catch (Exception callbackError) {
                log.error("callback.onError failed for agent={}", agentCode, callbackError);
            }
            return PipelineResult.builder()
                    .success(false)
                    .outputText(null)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
