package com.xiaomizhou.dpsk.db.chat;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.AgentCallback;
import com.xiaomizhou.dpsk.agent.GroupAgentCallback;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.agent.event.AgentEventType;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.ErrorNotifier;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.*;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.tool.ask.ToolAskManager;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * ImAgentCallback — AgentCallback 接口的 opc-im 实现。
 * 将 opc-core 发射的 AgentEvent 语义事件翻译为前端 WsMessage 协议，
 * 并通过 WebSocket 推送给前端，同时完成消息和 Token 的 DB 落库。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
public class ImAgentCallback implements AgentCallback, GroupAgentCallback {

    private final String userId;
    private final String conversationCode;
    private final String conversationType;
    private final String targetId;
    private final String taskId;
    private final ChatMessageComponent chatMessageComponent;
    private final TokenUsageDao tokenUsageDao;

    private final AgentDefProvider agentDefProvider;

    // 流式输出状态
    private String streamCode;
    private SenderInfo senderInfo;
    private final AtomicInteger chunkIndex = new AtomicInteger(0);

    private final AtomicInteger msgChunkIndex = new AtomicInteger(0);

    // 取消标记（由 AgentBridge 注入）
    private AtomicBoolean cancelFlag;

    // 累计 Token
    private TokenUsage accumulatedToken;

    // 是否已向该 stream 下发过错误，保证同一次执行只推一条 ERROR
    private final AtomicBoolean errorNotified = new AtomicBoolean(false);

    public ImAgentCallback(String userId,
                           String conversationCode,
                           String conversationType,
                           String targetId,
                           String taskId,
                           ChatMessageComponent chatMessageComponent,
                           TokenUsageDao tokenUsageDao,
                           AgentDefProvider agentDefProvider) {
        this.userId = userId;
        this.conversationCode = conversationCode;
        this.conversationType = conversationType;
        this.targetId = targetId;
        this.taskId = taskId;
        this.chatMessageComponent = chatMessageComponent;
        this.tokenUsageDao = tokenUsageDao;
        this.agentDefProvider = agentDefProvider;
    }

    /**
     * 设置 Agent 发送者信息，用于推送消息时携带。
     */
    public void setSenderInfo(SenderInfo info) {
        this.senderInfo = info;
    }

    /**
     * 由外部先设置好 streamCode，确保流式消息携带正确的流标识。
     */
    @Override
    public void setStreamCode(String streamCode) {
        this.streamCode = streamCode;
    }

    @Override
    public void setSenderInfo(String agentCode) {
        AgentDef agent = agentDefProvider.getByCode(agentCode);
        if (agent == null) {
            this.senderInfo = new SenderInfo(agentCode, agentCode, "", "");
            return;
        }
        this.senderInfo = new SenderInfo(agentCode, agent.getName(), agent.getAvatar(), agent.getNickname());
    }

    /**
     * 注入取消标记（由 AgentBridge 在 dispatch 时调用）。
     */
    public void setCancelFlag(AtomicBoolean cancelFlag) {
        this.cancelFlag = cancelFlag;
    }

    @Override
    public boolean isCancelled() {
        return cancelFlag != null && cancelFlag.get();
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        AgentEventType type = event.type();
        switch (type) {
            case THINKING -> handleThinking(event);
            case STREAM_CHUNK -> handleStreamChunk(event);
            case STREAM_CHUNK_END -> handleStreamChunkEnd(event);
            case MESSAGE -> handleMessage(event);
            case MESSAGE_CHUNK -> handleMessageChunk(event);
            case MESSAGE_CHUNK_END -> handleMessageChunkEnd(event);
            case DONE -> handleDone(event);
            case ERROR -> handleError(event);
            case TOOL_CALL -> handleToolCall(event);
            case TOOL_RESULT -> handleToolResult(event);
            case MSG_READ -> handleRead(event);
            case CANCELLED -> handleCancelled(event);
        }
    }

    private void handleCancelled(AgentEvent event) {
        if (StringUtils.isBlank(streamCode)) {
            return;
        }
        try {
            WsUtils.send(new WsMessage(WsMsgType.CANCEL, new StreamEndPayload(streamCode, null, taskId, null)));
        } catch (Exception e) {
            log.warn("Failed to send cancel event for stream {}!", streamCode, e);
        }
        // Agent 被取消时，取消该会话下所有待答复的 ask_user 提问，避免工具一直挂到超时
        try {
            ToolAskManager manager = ToolAskManager.instance();
            if (manager != null) {
                manager.cancelByConversation(conversationCode);
            }
        } catch (Exception e) {
            log.warn("Failed to cancel ask requests for conversation {}", conversationCode, e);
        }
    }

    private void handleToolResult(AgentEvent event) {
        String text = event.text();
        if (StringUtils.isBlank(text)) {
            return;
        }
        try {
            WsUtils.send(new WsMessage(WsMsgType.TOOL_RESULT, new ToolCallPayload(event.text(), streamCode, taskId, event.toolName(), event.toolInput(), event.toolOutput(), event.meta())));
        } catch (Exception e) {
            log.error("Failed to send tool call event for stream {}!", streamCode, e);
        }
    }


    private void handleToolCall(AgentEvent event) {
        String text = event.text();
        if (StringUtils.isBlank(text)) {
            return;
        }
        try {
            WsUtils.send(new WsMessage(WsMsgType.TOOL_CALL, new ToolCallPayload(event.text(), streamCode, taskId, event.toolName(), event.toolInput(), null, null)));
        } catch (Exception e) {
            log.error("Failed to send tool call event for stream {}!", streamCode, e);
        }
    }

    private void handleMessageChunkEnd(AgentEvent event) {
        if (streamCode != null) {
            try {
                WsUtils.send(new WsMessage(WsMsgType.MESSAGE_CHUNK_END,
                        new StreamEndPayload(streamCode, null, taskId, null)));
            } catch (Exception e) {
                log.error("Failed to send message stream end event for stream {}!", streamCode, e);
            }
        }
    }

    private void handleMessageChunk(AgentEvent event) {

        if (StringUtils.isBlank(streamCode) || null == event.text()) {
            return;
        }
        try {
            int index = msgChunkIndex.incrementAndGet();
            WsUtils.send(new WsMessage(WsMsgType.MESSAGE_CHUNK,
                    new StreamChunkPayload(streamCode, taskId, event.text(), index)));
        } catch (Exception e) {
            log.error("Failed to send stream end event for stream {}!", streamCode, e);
        }
    }


    private void handleMessage(AgentEvent event) {

        String text = event.text();
        if (streamCode == null) {
            return;
        }
        try {
            msgChunkIndex.set(0);
            WsUtils.send(new WsMessage(WsMsgType.MESSAGE,
                    new StreamChunkPayload(streamCode, taskId, text, 0)));
        } catch (Exception e) {
            log.warn("Failed to send message event for stream {}: {}", streamCode, e.getMessage());
        }
    }

    private void handleStreamChunkEnd(AgentEvent event) {
        if (streamCode != null) {
            try {
                WsUtils.send(new WsMessage(WsMsgType.STREAM_END,
                        new StreamEndPayload(streamCode, null, taskId, null)));
            } catch (Exception e) {
                log.error("Failed to send stream end event for stream {}!", streamCode, e);
            }
        }

    }

    private void handleRead(AgentEvent event) {
        String msgCode = event.text();
        if (StringUtils.isBlank(msgCode)) {
            return;
        }
        chatMessageComponent.delivered(List.of(msgCode));
        log.info("Message read: user={}, msg={}", userId, msgCode);
        try {
            WsUtils.send(new WsMessage(WsMsgType.READ_RECEIPT, new ReadReceiptPayload(conversationCode, msgCode)));
        } catch (Exception e) {
            log.warn("Failed to send message read event for message {}: {}", msgCode, e.getMessage());
        }
    }

    private void handleThinking(AgentEvent event) {
        if (streamCode == null) {
            return;
        }

        try {

            chunkIndex.set(0);
            WsUtils.send(new WsMessage(WsMsgType.STREAM_START,
                    new StreamStartPayload(streamCode, conversationCode, taskId, senderInfo, System.currentTimeMillis())));
        } catch (Exception e) {
            log.warn("Failed to send thinking event for stream {}: {}", streamCode, e.getMessage());
        }
    }

    private void handleStreamChunk(AgentEvent event) {
        String text = event.text();
        if (text == null || streamCode == null) {
            return;
        }

        try {
            int index = chunkIndex.incrementAndGet();
            WsUtils.send(new WsMessage(WsMsgType.STREAM_CHUNK,
                    new StreamChunkPayload(streamCode, taskId, text, index)));
        } catch (Exception e) {
            log.warn("Failed to send stream chunk for stream {}: {}", streamCode, e.getMessage());
        }
    }

    private void handleDone(AgentEvent event) {
        Map<String, Object> meta = event.meta();
        String content = meta != null ? (String) meta.get("content") : "";
        String contentType = meta != null ? (String) meta.get("contentType") : "text";
        Object tokenObj = meta != null ? meta.get("tokenUsage") : null;

        TokenUsage usage = null;
        if (tokenObj instanceof TokenUsage) {
            usage = (TokenUsage) tokenObj;
        }

        String agentCode = event.agentCode();

        // 保存消息到 DB
        String msgCode = saveMessageToDb(agentCode, content, usage);

        // 发送 stream_end
        try {

            // 发送完整消息
            Map<String, Object> fullContent = Maps.newHashMap();
            if (msgCode != null) {

                com.xiaomizhou.dpsk.db.model.TokenUsage tk = tokenUsageDao.getOneByMsgCode(msgCode);
                if (tk != null) {
                    fullContent.put("token", tk);
                }
            }

            WsUtils.send(new WsMessage(WsMsgType.MESSAGE_DONE,
                    new MessagePayload(
                            streamCode != null ? streamCode : msgCode,
                            conversationCode,
                            taskId,
                            contentType,
                            content,
                            senderInfo,
                            System.currentTimeMillis(),
                            "",
                            fullContent
                    )));
        } catch (Exception e) {
            log.warn("Failed to send done event for agent {}: {}", agentCode, e.getMessage());
        }
    }

    private void handleError(AgentEvent event) {
        log.error("Agent error: agent={}, message={}", event.agentCode(), event.text());
        sendError("AGENT_ERROR", event.text(), event.agentCode(), null);
    }

    @Override
    public void onComplete() {
        log.debug("Agent execution completed for stream={}", streamCode);
    }

    @Override
    public void onError(Throwable error) {
        // 注意：Throwable 必须作为日志最后一个参数传入，否则不会打印完整堆栈
        log.error("Agent execution error for stream={}, conversation={}, task={}",
                streamCode, conversationCode, taskId, error);
        sendError("EXECUTION_ERROR", error != null ? error.getMessage() : null,
                senderInfo != null ? senderInfo.userId() : null, error);
    }

    /**
     * 是否已经下发过错误事件，供上游（AgentBridge 等）判断，
     * 避免同一次执行重复推送 ERROR（例如 pipeline 内 onError 已推、外层再推一次）。
     */
    public boolean hasNotifiedError() {
        return errorNotified.get();
    }

    /**
     * 统一通过 WebSocket 推送错误信息给前端（含错误码、消息、异常类型、根因、堆栈与上下文）。
     * 同一次执行（同一 callback 实例）只下发一次，避免前端收到重复的 ERROR 事件。
     */
    private void sendError(String code, String message, String agentCode, Throwable error) {
        if (!errorNotified.compareAndSet(false, true)) {
            log.warn("Duplicate error suppressed: stream={}, code={}, message={}", streamCode, code, message);
            return;
        }
        ErrorNotifier.send(code, message, conversationCode, streamCode, taskId, agentCode, error);
    }

    /**
     * 保存消息到 DB。
     */
    private String saveMessageToDb(String agentCode, String content, TokenUsage usage) {
        try {
            if (ConversationType.GROUP.name().equalsIgnoreCase(conversationType)) {
                return chatMessageComponent.newGroupChatMsg(agentCode, ChatMsgDto.builder()
                        .sendId(agentCode)
                        .targetId(targetId)
                        .conversationType(ConversationType.GROUP.getCode())
                        .message(content)
                        .messageType("AI")
                        .parentMsgCode("")
                        .taskId(taskId)
                        .conversationCode(conversationCode)
                        .build(), usage, "deepseek-chat");
            } else if (ConversationType.WORKFLOW.name().equalsIgnoreCase(conversationType)) {
                return chatMessageComponent.newWorkflowMsg(agentCode, ChatMsgDto.builder()
                        .taskId(taskId)
                        .targetId(targetId)
                        .sendId(agentCode)
                        .conversationCode(conversationCode)
                        .conversationType(ConversationType.WORKFLOW.getCode())
                        .message(content)
                        .messageType("AI")
                        .parentMsgCode("")
                        .build(), usage, "deepseek-chat");
            } else {
                return chatMessageComponent.newSingleChatMsg(agentCode, ChatMsgDto.builder()
                        .targetId(userId)
                        .sendId(agentCode)
                        .message(content)
                        .messageType("AI")
                        .conversationType(ConversationType.SINGLE.getCode())
                        .conversationCode(conversationCode)
                        .taskId(taskId)
                        .build(), usage, "deepseek-chat");
            }
        } catch (Exception e) {
            log.error("Failed to save message to DB for agent={}", agentCode, e);
            return null;
        }
    }
}
