package com.xiaomizhou.dpsk.db.chat;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.db.*;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMemberDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.workflow.*;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlowToLiteFlowUtils;
import com.yomahub.liteflow.builder.LiteFlowNodeBuilder;
import com.yomahub.liteflow.builder.el.LiteFlowChainELBuilder;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.enums.NodeTypeEnum;
import com.yomahub.liteflow.flow.LiteflowResponse;
import com.yomahub.liteflow.property.LiteflowConfig;
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
import java.util.function.Function;
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
    private final WorkflowTaskComponent workflowTaskComponent;
    private final WorkflowTaskExecuteComponent workflowTaskExecuteComponent;
    private final ConversationDao conversationDao;

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
                       TokenUsageDao tokenUsageDao,
                       WorkflowTaskComponent workflowTaskComponent,
                       WorkflowTaskExecuteComponent workflowTaskExecuteComponent,
                       ConversationDao conversationDao) {
        this.orchestrator = orchestrator;
        this.agentDefProvider = agentDefProvider;
        this.agentComponent = agentComponent;
        this.chatMessageComponent = chatMessageComponent;
        this.chatGroupComponent = chatGroupComponent;
        this.tokenUsageDao = tokenUsageDao;
        this.workflowTaskComponent = workflowTaskComponent;
        this.workflowTaskExecuteComponent = workflowTaskExecuteComponent;
        this.conversationDao = conversationDao;
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

        // 2. 获取会话编码
        Conversation conv = conversationDao.getOneByCode(msg.getConversationCode());
        if (Objects.isNull(conv)) {
            return;
        }
        String conversationCode = conv.getCode();

        if (CollectionUtils.isNotEmpty(skillPaths)) {
            String template = "%s/%s/skills/%s/";
            skillPaths = skillPaths.stream().map(path -> template.formatted(skillPathPrefix, targetId, path)).toList();
        }

        // 3. 判断会话类型并组装 AgentBuildSpec
        if (ConversationType.GROUP.getCode().equals(conv.getConversationType())) {
            dispatchGroup(userId, msg, targetId, conversationCode, mcpCodes);
        } else if (ConversationType.SINGLE.getCode().equals(conv.getConversationType())) {
            dispatchSingle(userId, msg, targetId, conversationCode, mcpCodes, skillPaths);
        } else if (ConversationType.WORKFLOW.getCode().equals(conv.getConversationType())) {
            dispatchWorkflow(userId, msg, targetId, conversationCode);
        } else {
            throw new IllegalArgumentException("不支持的会话类型：" + conv.getConversationType());
        }
    }

    /**
     * 工作流分发。
     *
     * @param userId
     * @param msg
     * @param targetId
     * @param conversationCode
     */
    private void dispatchWorkflow(String userId, ChatMessage msg, String targetId, String conversationCode) {


        // 这一层构建工作流，NodeProcess层构建agent并执行
        WorkflowTaskDto task = workflowTaskComponent.getByCode(targetId);

        if (Objects.isNull(task)) {
            return;
        }

        XyFlow xyFlow = JsonUtils.toObj(task.getWorkflowJson(), XyFlow.class);
        List<NodeStep> steps = xyFlow.getSteps();
        List<String> agentCodes = steps.stream().map(NodeStep::getAgentCode).toList();
        Map<String, List<String>> mcpCodes = Maps.newHashMap();
        Map<String, List<String>> skillPaths = Maps.newHashMap();
        Map<String, String> prompts = Maps.newHashMap();
        steps.forEach(step -> {
            mcpCodes.put(step.getAgentCode(), step.getMcpCodes());
            skillPaths.put(step.getAgentCode(), step.getSkillPaths());
            prompts.put(step.getAgentCode(), step.getSystemPrompt());
        });

        // build lite flow node
        for (NodeStep step : steps) {


            if (NodeStep.NODE_TYPE_START.left.equalsIgnoreCase(step.getType())) {
                LiteFlowNodeBuilder.createCommonNode().setId(step.getId())
                        .setName(step.getId())
                        .setClazz(StartNodeProcessor.class)
                        .build();
            }

            if (NodeStep.NODE_TYPE_END.left.equalsIgnoreCase(step.getType())) {
                LiteFlowNodeBuilder.createCommonNode().setId(step.getId())
                        .setName(step.getId())
                        .setClazz(EndNodeProcessor.class)
                        .build();
            }

            if (NodeStep.NODE_TYPE_SWITCH.left.equalsIgnoreCase(step.getType())) {
                LiteFlowNodeBuilder.createCommonNode()
                        .setType(NodeTypeEnum.SWITCH)
                        .setId(step.getId())
                        .setName(step.getId())
                        .setClazz(SwitchNodeProcessor.class)
                        .build();
            }


            if (NodeStep.NODE_TYPE_COMMON_AGENT.left.equalsIgnoreCase(step.getType())) {
                LiteFlowNodeBuilder.createCommonNode()
                        .setId(step.getId())
                        .setName(step.getId())
                        .setClazz(AgentNodeProcessor.class)
                        .build();
            }
        }

        String el = XyFlowToLiteFlowUtils.toEl(xyFlow);

        log.info("task el. task:{},el:{}", task, el);
        LiteFlowChainELBuilder.createChain().setChainId(task.getCode()).setEL(el).build();

        LiteflowConfig config = new LiteflowConfig();

        config.setChainCacheEnabled(false);
        config.setSupportMultipleType(false);
        config.setEnableMonitorFile(true);
        config.setEnableLog(true);

        FlowExecutor executor = new FlowExecutor(config);

        // 构建NodeContext数组
        Map<String, NodeContext> nodes = steps.stream().map(step -> {
            return NodeContext.builder()
                    .nodeId(step.getId())
                    .nodeType(step.getType())
                    .nodeLabel(step.getLabel())
                    .mcpCodes(mcpCodes.get(step.getAgentCode()))
                    .skillPaths(skillPaths.get(step.getAgentCode()))
                    .prompt(prompts.get(step.getAgentCode()))
                    .agentCode(step.getAgentCode())
                    .build();
        }).collect(Collectors.toMap(NodeContext::getNodeId, Function.identity()));

        AtomicBoolean cancelFlag = getOrCreateCancelFlag(msg.getCode());

        WorkflowContext context = WorkflowContext.builder()
                .userId(userId)
                .targetId(targetId)
                .nodes(nodes)
                .msgCode(msg.getCode())
                .conversationCode(conversationCode)
                .orchestrator(orchestrator)
                .workflowTaskComponent(workflowTaskComponent)
                .chatMessageComponent(chatMessageComponent)
                .tokenUsageDao(tokenUsageDao)
                .agentDefProvider(agentDefProvider)
                .cancelFlag(cancelFlag)
                .workflowTaskExecuteComponent(workflowTaskExecuteComponent)
                .build();

        LiteflowResponse response = executor.execute2Resp(task.getCode(), "上下文参数", context);

        log.info("task response. task:{},response:{}", task, response);
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
