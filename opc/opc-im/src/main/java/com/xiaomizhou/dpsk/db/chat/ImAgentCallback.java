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
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.*;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dao.AgentDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private boolean streamStarted = false;

    // 累计 Token
    private TokenUsage accumulatedToken;

    public ImAgentCallback(String userId,
                           String conversationCode,
                           String conversationType,
                           String targetId,
                           String taskId,
                           ChatMessageComponent chatMessageComponent,
                           TokenUsageDao tokenUsageDao, AgentDefProvider agentDefProvider) {
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
        this.senderInfo = new SenderInfo(agentCode, agent.getNickname(), agent.getAvatar());
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
            case DONE -> handleDone(event);
            case ERROR -> handleError(event);
            case TOOL_CALL -> log.debug("Tool call: agent={}, tool={}", event.agentCode(), event.toolName());
            case TOOL_RESULT -> log.debug("Tool result: agent={}, tool={}", event.agentCode(), event.toolName());
            case MSG_READ -> handleRead(event);
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
        String text = event.text();
        if (text == null || streamCode == null) {
            return;
        }

        try {
            if (!streamStarted) {
                streamStarted = true;
                chunkIndex.set(0);
                if (senderInfo != null) {
                    WsUtils.send(new WsMessage(WsMsgType.STREAM_START,
                            new StreamStartPayload(streamCode, conversationCode,
                                    senderInfo, System.currentTimeMillis())));
                }
            }

            int index = chunkIndex.incrementAndGet();
            WsUtils.send(new WsMessage(WsMsgType.STREAM_CHUNK,
                    new StreamChunkPayload(streamCode, text, index)));
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
            if (!streamStarted) {
                streamStarted = true;
                chunkIndex.set(0);
                if (senderInfo != null) {
                    WsUtils.send(new WsMessage(WsMsgType.STREAM_START,
                            new StreamStartPayload(streamCode, conversationCode,
                                    senderInfo, System.currentTimeMillis())));
                }
            }

            int index = chunkIndex.incrementAndGet();
            WsUtils.send(new WsMessage(WsMsgType.STREAM_CHUNK,
                    new StreamChunkPayload(streamCode, text, index)));
        } catch (Exception e) {
            log.warn("Failed to send stream chunk for stream {}: {}", streamCode, e.getMessage());
        }
    }

    private void handleDone(AgentEvent event) {
        Map<String, Object> meta = event.meta();
        String content = meta != null ? (String) meta.get("content") : "";
        Object tokenObj = meta != null ? meta.get("tokenUsage") : null;

        if (tokenObj instanceof TokenUsage) {
            accumulatedToken = (TokenUsage) tokenObj;
        }

        String agentCode = event.agentCode();

        // 保存消息到 DB
        String msgCode = saveMessageToDb(agentCode, content);

        // 发送 stream_end
        try {
            if (streamCode != null) {
                WsUtils.send(new WsMessage(WsMsgType.STREAM_END,
                        new StreamEndPayload(streamCode, msgCode, null)));
            }

            // 发送完整消息
            Map<String, Object> fullContent = Maps.newHashMap();
            if (msgCode != null) {

                com.xiaomizhou.dpsk.db.model.TokenUsage tk = tokenUsageDao.getOneByMsgCode(msgCode);
                if (tk != null) {
                    fullContent.put("token", tk);
                }
            }

            WsUtils.send(new WsMessage(WsMsgType.MESSAGE,
                    new MessagePayload(
                            streamCode != null ? streamCode : msgCode,
                            conversationCode,
                            "text",
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
        try {
            WsUtils.send(new WsMessage(WsMsgType.ERROR,
                    Map.of("code", "AGENT_ERROR", "message", event.text(), "agent", event.agentCode())));
        } catch (Exception e) {
            log.warn("Failed to send error event", e);
        }
    }

    @Override
    public void onComplete() {
        log.debug("Agent execution completed for stream={}", streamCode);
    }

    @Override
    public void onError(Throwable error) {
        log.error("Agent execution error for stream={}: {}", streamCode, error.getMessage());
        try {
            WsUtils.send(new WsMessage(WsMsgType.ERROR,
                    Map.of("code", "EXECUTION_ERROR", "message", error.getMessage())));
        } catch (Exception e) {
            log.warn("Failed to send error notification", e);
        }
    }

    /**
     * 保存消息到 DB。
     */
    private String saveMessageToDb(String agentCode, String content) {
        try {
            if (ConversationType.GROUP.name().equalsIgnoreCase(conversationType)) {
                return chatMessageComponent.newGroupChatMsg(agentCode, ChatMsgDto.builder()
                        .taskId(targetId)
                        .sendId(agentCode)
                        .targetId(targetId)
                        .conversationType(ConversationType.GROUP.getCode())
                        .message(content)
                        .messageType("AI")
                        .parentMsgCode("")
                        .taskId(taskId)
                        .mentionedList(java.util.List.of())
                        .conversationCode(conversationCode)
                        .build(), accumulatedToken, "deepseek-chat");
            } else {
                return chatMessageComponent.newSingleChatMsg(agentCode, ChatMsgDto.builder()
                        .targetId(userId)
                        .sendId(agentCode)
                        .message(content)
                        .messageType("AI")
                        .conversationType(ConversationType.SINGLE.getCode())
                        .conversationCode(conversationCode)
                        .build(), accumulatedToken, "deepseek-chat");
            }
        } catch (Exception e) {
            log.error("Failed to save message to DB for agent={}", agentCode, e);
            return null;
        }
    }

    /** 获取累计的 Token 用量 */
    public TokenUsage getAccumulatedToken() {
        return accumulatedToken;
    }
}
