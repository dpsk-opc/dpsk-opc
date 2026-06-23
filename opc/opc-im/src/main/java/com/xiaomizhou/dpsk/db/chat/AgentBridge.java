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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Value("${com.xiaomizhou.dpsk.opc.skill.path:~/skills}")
    private String skillPathPrefix;

    /**
     * 取消标记映射：msgCode -> 取消标记。
     * 前端调用 cancel 接口时设置，Pipeline 在执行循环中轮询。
     */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

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
     * 取消指定消息对应的 Agent 会话。
     *
     * @param msgCode 用户发送的消息编码
     * @return true 表示成功设置取消标记，false 表示该消息不存在或已完成
     */
    public boolean cancel(String msgCode) {
        AtomicBoolean flag = cancelFlags.get(msgCode);
        if (flag == null) {
            log.warn("Cancel failed: no active session for msgCode={}", msgCode);
            return false;
        }
        boolean wasCancelled = flag.compareAndSet(false, true);
        if (wasCancelled) {
            log.info("Session cancelled: msgCode={}", msgCode);
            // 通知前端会话已取消
            try {
                WsUtils.send(new WsMessage(WsMsgType.CANCEL, Map.of("msgCode", msgCode)));
            } catch (Exception e) {
                log.warn("Failed to send cancel WS notification for msgCode={}", msgCode, e);
            }
        }
        return wasCancelled;
    }

    /**
     * 获取或创建 msgCode 对应的取消标记。
     * dispatch 时调用，dispatch 结束后由 finally 清理。
     */
    private AtomicBoolean getOrCreateCancelFlag(String msgCode) {
        return cancelFlags.computeIfAbsent(msgCode, k -> new AtomicBoolean(false));
    }

    /**
     * 清理取消标记（会话结束后调用）。
     */
    private void clearCancelFlag(String msgCode) {
        cancelFlags.remove(msgCode);
    }

    /**
     * 聊天分发入口。
     *
     * @param userId  当前用户编码
     * @param msgCode 用户发送的消息编码
     */
    public void dispatch(String userId, String msgCode,List<String> mcpCodes,List<String> skillPaths) {
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

        if (CollectionUtils.isNotEmpty(skillPaths)) {
            String template = "%s/%s/skills/%s/";
            skillPaths = skillPaths.stream().map(path -> template.formatted(skillPathPrefix, targetId, path)).toList();
        }

        // 3. 判断会话类型并组装 AgentBuildSpec
        if (ConversationType.GROUP.name().equalsIgnoreCase(conversationType)) {
            dispatchGroup(userId, msg, targetId, conversationCode, mcpCodes);
        } else {
            dispatchSingle(userId, msg, targetId, conversationCode, mcpCodes, skillPaths);
        }
    }

    /**
     * 单聊分发。
     */
    private void dispatchSingle(String userId,
                                com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                                String targetId,
                                String conversationCode,
                                List<String> mcpCodes,
                                List<String> skillPaths) {
        AgentDto agent = agentComponent.getByCode(targetId);
        if (agent == null) {
            return;
        }

        String msgCode = msg.getCode();
        AtomicBoolean cancelFlag = getOrCreateCancelFlag(msgCode);

        try {
            // 组装 AgentBuildSpec
            AgentBuildSpec spec = AgentBuildSpec.builder()
                    .mode(AgentBuildSpec.MODE_SINGLE)
                    .userCode(userId)
                    .targetAgentCode(targetId)
                    .userContent(msg.getContent())
                    .conversationCode(conversationCode)
                    .mcpCodes(mcpCodes)
                    .skillPaths(skillPaths)
                    .build();

            // 生成流式编码
            String streamCode = SequenceUtils.generator().next("STM");

            // 创建回调，注入取消标记
            SenderInfo senderInfo = new SenderInfo(agent.getCode(), agent.getName(), agent.getAvatar());
            ImAgentCallback callback = new ImAgentCallback(
                    userId, conversationCode,
                    ConversationType.SINGLE.name(), targetId, msg.getTaskId(),
                    chatMessageComponent, tokenUsageDao, agentDefProvider);
            callback.setStreamCode(streamCode);
            callback.setSenderInfo(senderInfo);
            callback.setCancelFlag(cancelFlag);

            callback.onEvent(AgentEvent.msgRead(agent.getCode(), msg.getCode()));

            PipelineResult result = orchestrator.execute(spec, callback);

            log.info("Single chat completed: agent={}, success={}, contentLen={}",
                    targetId, result.isSuccess(),
                    result.getOutputText() != null ? result.getOutputText().length() : 0);
        } finally {
            clearCancelFlag(msgCode);
        }
    }

    /**
     * 群聊分发。
     */
    private void dispatchGroup(String userId,
                               com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                               String targetId,
                               String conversationCode,
                               List<String> mcpCodes) {
        String msgCode = msg.getCode();
        AtomicBoolean cancelFlag = getOrCreateCancelFlag(msgCode);

        try {
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

            // 创建回调（群聊用 group 信息），注入取消标记
            ImAgentCallback callback = new ImAgentCallback(
                    userId, conversationCode,
                    ConversationType.GROUP.name(), targetId, msg.getTaskId(),
                    chatMessageComponent, tokenUsageDao, agentDefProvider);
            callback.setStreamCode(streamCode);
            callback.setSenderInfo(new SenderInfo(userId, userId, ""));
            callback.setCancelFlag(cancelFlag);

            callback.onEvent(AgentEvent.msgRead(userId, msg.getCode()));

            PipelineResult result = orchestrator.execute(spec, callback);
            log.info("Group chat completed: group={}, agentCount={}, success={}",
                    targetId, agentCodes.size(), result.isSuccess());
        } finally {
            clearCancelFlag(msgCode);
        }
    }
}
