package com.xiaomizhou.dpsk.db.chat;

import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.constant.MessageStatus;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.StreamEndPayload;
import com.xiaomizhou.dpsk.db.AgentComponent;
import com.xiaomizhou.dpsk.db.ChatGroupComponent;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMemberDto;
import com.xiaomizhou.dpsk.task.TaskCreationContext;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AgentBridge — IM 侧总闸门，替代当前 ChatService 的核心编排逻辑。
 * <p>
 * 职责：
 * <ol>
 *   <li>接收用户消息</li>
 *   <li>查询 DB 组装 AgentBuildSpec</li>
 *   <li>调用 AgentOrchestrator.execute()</li>
 *   <li>创建 ImAgentCallback，将 AgentEvent 翻译为 WsMessage 推送</li>
 *   <li>委托 ImAgentCallback 完成 DB 落库</li>
 * </ol>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Component
@Slf4j
public class AgentBridge {

    private final AgentOrchestrator orchestrator;
    private final AgentDefProvider agentDefProvider;
    private final AgentComponent agentComponent;
    private final ChatMessageComponent chatMessageComponent;
    private final ChatGroupComponent chatGroupComponent;
    private final TokenUsageDao tokenUsageDao;

    public AgentBridge(AgentOrchestrator orchestrator,
                       AgentDefProvider agentDefProvider,
                       AgentComponent agentComponent,
                       ChatMessageComponent chatMessageComponent,
                       ChatGroupComponent chatGroupComponent,
                       TokenUsageDao tokenUsageDao) {
        this.orchestrator = orchestrator;
        this.agentDefProvider = agentDefProvider;
        this.agentComponent = agentComponent;
        this.chatMessageComponent = chatMessageComponent;
        this.chatGroupComponent = chatGroupComponent;
        this.tokenUsageDao = tokenUsageDao;
    }

    /**
     * 聊天分发入口。
     *
     * @param userId  当前用户编码
     * @param msgCode 用户发送的消息编码
     */
    public void dispatch(String userId, String msgCode,List<String> mcpCodes) {
        if (StringUtils.isBlank(msgCode)) {
            return;
        }

        // 1. 查询消息
        com.xiaomizhou.dpsk.db.model.ChatMessage msg = chatMessageComponent.getByCode(msgCode);
        if (msg == null) {
            return;
        }

        String targetId = msg.getReceiverCode();
        String conversationType = msg.getConversationType();

        // 2. 获取会话编码
        ImmutablePair<String, ConversationType> conv = chatMessageComponent.getConversationCode(
                msg.getSenderCode(), msg.getReceiverCode());
        if (conv.left == null || conv.right == null) {
            return;
        }
        String conversationCode = conv.left;

        // 3. 判断会话类型并组装 AgentBuildSpec
        if (ConversationType.GROUP.name().equalsIgnoreCase(conversationType)) {
            dispatchGroup(userId, msg, targetId, conversationCode,mcpCodes);
        } else {
            dispatchSingle(userId, msg, targetId, conversationCode,mcpCodes);
        }
    }

    /**
     * 单聊分发。
     */
    private void dispatchSingle(String userId,
                                com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                                String targetId,
                                String conversationCode,
                                List<String> mcpCodes) {
        AgentDto agent = agentComponent.getByCode(targetId);
        if (agent == null) {
            return;
        }

        // 组装 AgentBuildSpec
        AgentBuildSpec spec = AgentBuildSpec.builder()
                .mode(AgentBuildSpec.MODE_SINGLE)
                .userCode(userId)
                .targetAgentCode(targetId)
                .userContent(msg.getContent())
                .conversationCode(conversationCode)
                .mcpCodes(mcpCodes)
                .build();

        // 生成流式编码
        String streamCode = SequenceUtils.generator().next("STM");

        // 创建回调
        SenderInfo senderInfo = new SenderInfo(agent.getCode(), agent.getName(), agent.getAvatar());
        ImAgentCallback callback = new ImAgentCallback(
                userId, conversationCode,
                ConversationType.SINGLE.name(), targetId, msg.getTaskId(),
                chatMessageComponent, tokenUsageDao, agentDefProvider);
        callback.setStreamCode(streamCode);
        callback.setSenderInfo(senderInfo);

        callback.onEvent(AgentEvent.msgRead(agent.getCode(), msg.getCode()));

        PipelineResult result = orchestrator.execute(spec, callback);


        log.info("Single chat completed: agent={}, success={}, contentLen={}",
                targetId, result.isSuccess(),
                result.getOutputText() != null ? result.getOutputText().length() : 0);
    }

    /**
     * 群聊分发。
     */
    private void dispatchGroup(String userId,
                               com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                               String targetId,
                               String conversationCode,
                               List<String> mcpCodes) {
        // 获取群成员
        List<ChatMemberDto> members = chatGroupComponent.getGroupMembers(targetId);
        if (CollectionUtils.isEmpty(members)) {
            return;
        }

        // 排除发言用户
        members = members.stream()
                .filter(member -> !member.getCode().equalsIgnoreCase(userId))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(members)) {
            return;
        }

        List<String> agentCodes = members.stream()
                .map(ChatMemberDto::getCode)
                .collect(Collectors.toList());

        // 组装 AgentBuildSpec
        AgentBuildSpec spec = AgentBuildSpec.builder()
                .mode(AgentBuildSpec.MODE_GROUP)
                .userCode(userId)
                .targetAgentCodes(agentCodes)
                .groupCode(targetId)
                .userContent(msg.getContent())
                .conversationCode(conversationCode)
                .mcpCodes(mcpCodes)
                .build();

        // 生成流式编码
        String streamCode = SequenceUtils.generator().next("STM");

        // 创建回调（群聊用 group 信息）
        ImAgentCallback callback = new ImAgentCallback(
                userId, conversationCode,
                ConversationType.GROUP.name(), targetId, msg.getTaskId(),
                chatMessageComponent, tokenUsageDao, agentDefProvider);
        callback.setStreamCode(streamCode);
        callback.setSenderInfo(new SenderInfo(userId, userId, ""));

        callback.onEvent(AgentEvent.msgRead(userId, msg.getCode()));

        PipelineResult result = orchestrator.execute(spec, callback);
        log.info("Group chat completed: group={}, agentCount={}, success={}",
                targetId, agentCodes.size(), result.isSuccess());
    }
}
