package com.xiaomizhou.dpsk.db.chat;

import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.event.AgentEvent;
import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.payload.MessagePayload;
import com.xiaomizhou.dpsk.db.*;
import com.xiaomizhou.dpsk.db.dao.ConversationDao;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.AgentDto;
import com.xiaomizhou.dpsk.db.dto.ChatMemberDto;
import com.xiaomizhou.dpsk.db.dto.ChatMsgDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateDto;
import com.xiaomizhou.dpsk.db.model.ChatMessage;
import com.xiaomizhou.dpsk.db.model.Conversation;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.planning.*;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.workflow.WorkflowConfirmManager;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.XyFlowContextBuilder;
import com.xiaomizhou.dpsk.workflow.langgraph.LangGraphWorkflowEngine;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
    private final WorkflowTaskComponent workflowTaskComponent;
    private final WorkflowTaskExecuteComponent workflowTaskExecuteComponent;
    private final ConversationDao conversationDao;
    private final ExecutorService executorService;
    private final WorkflowPlanner workflowPlanner;
    private final PlanValidator planValidator;
    private final WorkflowTaskFactory workflowTaskFactory;
    private final GroupIntentClassifier groupIntentClassifier;
    private final ExpertIntentClassifier expertIntentClassifier;
    private final WorkflowTemplateComponent workflowTemplateComponent;

    @Value("${com.xiaomizhou.dpsk.opc.skill.path:~/skills}")
    private String skillPathPrefix;

    /** 群聊自主规划 replan 总开关 */
    @Value("${workflow.replan.enabled:true}")
    private boolean replanEnabled;

    /** 群聊 replan 最大轮次（replan 次数上限） */
    @Value("${workflow.replan.max-retry:2}")
    private int replanMaxRounds;

    /**
     * 取消标记映射：msgCode -> 取消标记。
     * 前端调用 cancel 接口时设置，Pipeline 在执行循环中轮询。
     */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 工作流确认管理器。
     */
    private final WorkflowConfirmManager confirmManager = new WorkflowConfirmManager();

    public AgentBridge(AgentOrchestrator orchestrator,
                       AgentDefProvider agentDefProvider,
                       AgentComponent agentComponent,
                       ChatMessageComponent chatMessageComponent,
                       ChatGroupComponent chatGroupComponent,
                       TokenUsageDao tokenUsageDao,
                       WorkflowTaskComponent workflowTaskComponent,
                       WorkflowTaskExecuteComponent workflowTaskExecuteComponent,
                       ConversationDao conversationDao,
                       ExecutorService executorService,
                       WorkflowPlanner workflowPlanner,
                       PlanValidator planValidator,
                       WorkflowTaskFactory workflowTaskFactory,
                       GroupIntentClassifier groupIntentClassifier,
                       ExpertIntentClassifier expertIntentClassifier,
                       WorkflowTemplateComponent workflowTemplateComponent) {
        this.orchestrator = orchestrator;
        this.agentDefProvider = agentDefProvider;
        this.agentComponent = agentComponent;
        this.chatMessageComponent = chatMessageComponent;
        this.chatGroupComponent = chatGroupComponent;
        this.tokenUsageDao = tokenUsageDao;
        this.workflowTaskComponent = workflowTaskComponent;
        this.workflowTaskExecuteComponent = workflowTaskExecuteComponent;
        this.conversationDao = conversationDao;
        this.executorService = executorService;
        this.workflowPlanner = workflowPlanner;
        this.planValidator = planValidator;
        this.workflowTaskFactory = workflowTaskFactory;
        this.groupIntentClassifier = groupIntentClassifier;
        this.expertIntentClassifier = expertIntentClassifier;
        this.workflowTemplateComponent = workflowTemplateComponent;
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

    public boolean workFlowConfirm(WorkflowConfirmManager.WorkflowConfirmDto dto) {

        if (StringUtils.isAnyBlank(dto.getNodeId(), dto.getNodeId())) {
            return false;
        }

        WorkflowNodeLogDO node = workflowTaskExecuteComponent.getOne(dto.getTaskCode(), dto.getNodeId());

        try {

            // 任务已经是挂起状态
            if (WorkflowNodeLogDO.STATUS_PENDING == node.getStatus()) {
                workflowTaskExecuteComponent.end(node.getId(), WorkflowNodeLogDO.STATUS_SUCCESS, null, "", null, dto.getConfirmReason());
                String taskCode = dto.getTaskCode();
                WorkflowTaskDto task = workflowTaskComponent.getByCode(taskCode);

                // 重启任务
                executorService.execute(() -> dispatchWorkflow(dto.getUserId(), "", taskCode, task.getTemplateCode(), task.getConversationCode()));
                return true;
            }

            // 还在等待，确认
            confirmManager.confirm(dto.getTaskCode(), dto.getNodeId(), dto);
        } catch (InterruptedException e) {
            log.error("workFlowConfirm error", e);
            return false;
        }
        return true;
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
    public void dispatch(String userId, String msgCode, List<String> mcpCodes, List<String> skillPaths, ChatMsgDto.ImageGenerateDto imageGenerateDto) {
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

            if (Objects.nonNull(imageGenerateDto)) {
                dispatchImageGenerate(userId, msg, targetId, conversationCode,imageGenerateDto);
                return;
            }

            dispatchSingle(userId, msg, targetId, conversationCode, mcpCodes, skillPaths);
        } else if (ConversationType.WORKFLOW.getCode().equals(conv.getConversationType())) {
            dispatchWorkflow(userId, msg.getContent(), msg.getTaskId(), targetId, conversationCode);
        } else {
            throw new IllegalArgumentException("不支持的会话类型：" + conv.getConversationType());
        }
    }

    private void dispatchImageGenerate(String userId, ChatMessage msg, String targetId, String conversationCode, ChatMsgDto.ImageGenerateDto imageGenerateDto) {

        AgentDto agent = agentComponent.getByCode(targetId);
        if (agent == null || !Objects.equals(1, agent.getModality())) {
            log.warn("agent is not image generate agent.");
            return;
        }



        try {
            // 组装 AgentBuildSpec
            AgentBuildSpec spec = AgentBuildSpec.builder()
                    .mode(AgentBuildSpec.MODE_IMAGE)
                    .userCode(userId)
                    .targetAgentCode(targetId)
                    .userContent(msg.getContent())
                    .conversationCode(conversationCode)
                    .imageBuildSpec(AgentBuildSpec.ImageBuildSpec.builder()
                            .n(imageGenerateDto.getCount())
                            .mode(imageGenerateDto.getMode())
                            .size(imageGenerateDto.getSize())
                            .urls(imageGenerateDto.getUrls())
                            .build())
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

            callback.onEvent(AgentEvent.msgRead(agent.getCode(), msg.getCode()));

            PipelineResult result = orchestrator.execute(spec, callback);

            log.info("Single image chat completed: agent={}, success={}, contentLen={}",
                    targetId, result.isSuccess(),
                    result.getOutputText() != null ? result.getOutputText().length() : 0);
        } catch (Exception e) {
            log.error("dispatchImageGenerate error", e);
        }
    }

    /**
     * 工作流分发。
     *
     * @param userId
     * @param contextData
     * @param taskCode
     * @param targetId
     * @param conversationCode
     */
    public void dispatchWorkflow(String userId, String contextData,String taskCode, String targetId, String conversationCode) {

        WorkflowTaskDto task = workflowTaskComponent.getByCode(taskCode);

        if (Objects.isNull(task)) {
            return;
        }

        // 意图识别 + 相关性判断：仅对新用户消息（contextData 非空）执行；
        // 人工确认恢复执行（contextData=""）不做拦截，直接继续跑图。
        if (StringUtils.isNotBlank(contextData)) {
            ExpertIntentClassifier.Verdict verdict = judgeExpertTask(contextData, task);
            if (verdict != ExpertIntentClassifier.Verdict.PASS) {
                sendExpertHint(verdict, userId, task, conversationCode);
                return;
            }
        }

        XyFlow xyFlow = JsonUtils.toObj(task.getWorkflowJson(), XyFlow.class);

        AtomicBoolean cancelFlag = getOrCreateCancelFlag(taskCode);

        // 构造引擎无关的 WorkflowContext（LangGraph4j 执行）
        WorkflowContext context = XyFlowContextBuilder.build(xyFlow, task, userId, targetId, conversationCode,
                contextData, cancelFlag, confirmManager, orchestrator, workflowTaskComponent,
                chatMessageComponent, tokenUsageDao, agentDefProvider, workflowTaskExecuteComponent);

        // LangGraph 引擎执行专家团人工定义图（支持 switch / loop 等回路结构）
        new LangGraphWorkflowEngine().execute(xyFlow, context);
    }

    /**
     * 专家团入口意图识别：非任务或任务与专家团不相关时返回对应 Verdict，可执行返回 PASS。
     * LLM 判断异常时保守放行（PASS）。
     */
    private ExpertIntentClassifier.Verdict judgeExpertTask(String userContent, WorkflowTaskDto task) {
        WorkflowTemplateDto template = null;
        if (StringUtils.isNotBlank(task.getTemplateCode())) {
            template = workflowTemplateComponent.getByCode(task.getTemplateCode());
        }
        return expertIntentClassifier.evaluate(userContent, template);
    }

    /** 向用户推送专家团入口提示消息（非任务 / 任务与专家团不相关）。 */
    private void sendExpertHint(ExpertIntentClassifier.Verdict verdict, String userId,
                                WorkflowTaskDto task, String conversationCode) {
        String hint = ExpertIntentClassifier.Verdict.NOT_TASK.equals(verdict)
                ? "这里是「" + (task != null && StringUtils.isNotBlank(task.getName()) ? task.getName() : "专家团") + "」任务模式，请输入具体任务描述，我会帮你执行。"
                : "您输入的内容与本专家团的能力不匹配，请描述一个与当前专家团定位相符的具体任务。";
        try {
            SenderInfo sender = new SenderInfo(userId, userId, "");
            WsUtils.send(new WsMessage(WsMsgType.MESSAGE_DONE,
                    new MessagePayload(SequenceUtils.generator().next("MSG"), conversationCode,
                            Objects.isNull(task) ? "" : task.getCode(),
                            "text", hint, sender, System.currentTimeMillis(), null, null)));
        } catch (Exception e) {
            log.warn("send expert hint failed, conversationCode={}, verdict={}",
                    conversationCode, verdict, e);
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
     * 群聊分发入口：先做意图识别，再决定走闲聊对话（CHAT）还是任务编排（TASK）。
     * <p>
     * CHAT — 由群聊 Supervisor（suggestedAgent 机制）选一个合适的 Agent 直接对话，不建任务、不做编排；
     * TASK — 自主规划 → 落库 → LangGraph 执行 → 失败时 replan。
     */
    private void dispatchGroup(String userId,
                               com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                               String targetId,
                               String conversationCode,
                               List<String> mcpCodes) {
        String msgCode = msg.getCode();

        // 意图识别前置：闲聊走 CHAT，复杂任务走 TASK
        String intent = groupIntentClassifier.classify(msg.getContent());
        if (GroupIntentClassifier.INTENT_CHAT.equals(intent)) {
            dispatchGroupChat(userId, msg, targetId, conversationCode, mcpCodes, msgCode);
        } else {
            dispatchGroupTask(userId, msg, targetId, conversationCode, mcpCodes, msgCode);
        }
    }

    /**
     * 群聊闲聊分发：复用群聊 Supervisor（MODE_GROUP / GroupBuilder），
     * 由 supervisor 依据各 Agent 人设决定谁最适合回答（即第一版 suggestedAgent 机制）。
     * 不创建工作流任务、不做编排。
     */
    private void dispatchGroupChat(String userId,
                                   com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                                   String targetId,
                                   String conversationCode,
                                   List<String> mcpCodes,
                                   String msgCode) {
        AtomicBoolean cancelFlag = getOrCreateCancelFlag(msgCode);
        try {
            List<String> agentCodes = groupAgentCodes(userId, targetId);
            if (agentCodes.isEmpty()) {
                return;
            }

            // 组装 MODE_GROUP 的 spec（Supervisor 群聊对话）
            AgentBuildSpec spec = AgentBuildSpec.builder()
                    .mode(AgentBuildSpec.MODE_GROUP)
                    .userCode(userId)
                    .targetAgentCodes(agentCodes)
                    .groupCode(targetId)
                    .userContent(msg.getContent())
                    .conversationCode(conversationCode)
                    .mcpCodes(mcpCodes)
                    .build();

            String streamCode = SequenceUtils.generator().next("STM");

            ImAgentCallback callback = new ImAgentCallback(
                    userId, conversationCode,
                    ConversationType.GROUP.name(), targetId, msg.getTaskId(),
                    chatMessageComponent, tokenUsageDao, agentDefProvider);
            callback.setStreamCode(streamCode);
            callback.setSenderInfo(new SenderInfo(userId, userId, ""));
            callback.setCancelFlag(cancelFlag);

            callback.onEvent(AgentEvent.msgRead(userId, msg.getCode()));

            PipelineResult result = orchestrator.execute(spec, callback);
            log.info("Group chat (CHAT) completed: group={}, agentCount={}, success={}",
                    targetId, agentCodes.size(), result.isSuccess());
        } finally {
            clearCancelFlag(msgCode);
        }
    }

    /**
     * 群聊任务分发：自主规划 → 落库 → LangGraph 执行 → 失败时 replan（最多 replanMaxRounds 轮）。
     */
    private void dispatchGroupTask(String userId,
                                   com.xiaomizhou.dpsk.db.model.ChatMessage msg,
                                   String targetId,
                                   String conversationCode,
                                   List<String> mcpCodes,
                                   String msgCode) {
        AtomicBoolean cancelFlag = getOrCreateCancelFlag(msgCode);

        try {
            // 1. 获取群成员（候选 Agent 池），排除发言用户
            List<String> agentCodes = groupAgentCodes(userId, targetId);
            if (agentCodes.isEmpty()) {
                return;
            }

            String userContent = msg.getContent();

            // 2. 首次规划（无历史）；校验不通过 → 落库一条 FAILED 任务（D2：直接失败不降级）
            XyFlow xyFlow = plan(userId, targetId, conversationCode, agentCodes, userContent,
                    List.of(), false);
            if (xyFlow == null) {
                log.warn("群聊规划失败（校验不通过），直接 FAILED：group={}", targetId);
                WorkflowTaskDto failed = workflowTaskFactory.addByPlan(targetId, conversationCode,
                        agentCodes.get(0), "群聊任务_" + SequenceUtils.generator().next("N"),
                        "", userContent);
                if (failed != null && StringUtils.isNotBlank(failed.getCode())) {
                    workflowTaskComponent.failByCode(failed.getCode(), "规划校验不通过");
                }
                return;
            }

            // 3. 落库任务
            WorkflowTaskDto task = workflowTaskFactory.addByPlan(targetId, conversationCode,
                    agentCodes.get(0), "群聊任务_" + SequenceUtils.generator().next("N"),
                    JsonUtils.toJson(xyFlow), userContent);
            if (task == null || StringUtils.isBlank(task.getCode())) {
                log.warn("群聊任务落库失败：group={}", targetId);
                return;
            }

            // 4. LangGraph 执行 + replan 循环
            String taskCode = task.getCode();
            List<PlanningRequest.NodeResultRef> history = new java.util.ArrayList<>();
            String userContentForReplan = userContent;

            int maxRounds = replanEnabled ? replanMaxRounds : 0;
            for (int round = 0; round <= maxRounds; round++) {
                boolean isReplan = round > 0;

                // 4.1 重新规划（replan 时用 history + 失败原因）
                if (isReplan) {
                    xyFlow = plan(userId, targetId, conversationCode, agentCodes, userContentForReplan,
                            history, true);
                    if (xyFlow == null) {
                        workflowTaskComponent.failByCode(taskCode, "replan 规划校验不通过");
                        return;
                    }
                    workflowTaskComponent.updateWorkflowJsonAndReset(taskCode, JsonUtils.toJson(xyFlow));
                }

                // 4.2 构建上下文并执行（conversationType=GROUP，targetId=groupCode，thinking 落群会话）
                WorkflowContext context = XyFlowContextBuilder.build(xyFlow, task, userId, targetId,
                        conversationCode, ConversationType.GROUP.name(), targetId, userContentForReplan,
                        cancelFlag, confirmManager, orchestrator, workflowTaskComponent,
                        chatMessageComponent, tokenUsageDao, agentDefProvider, workflowTaskExecuteComponent);

                LangGraphWorkflowEngine.ExecutionResult exec = new LangGraphWorkflowEngine().execute(xyFlow, context);
                if (exec.isSuccess()) {
                    workflowTaskComponent.completeByCode(taskCode);
                    log.info("群聊规划执行成功：group={}, taskCode={}, round={}", targetId, taskCode, round);
                    return;
                }

                // 4.3 执行失败：记录失败原因 + 上游节点结果，进入 replan
                String reason = exec.getReason() != null ? exec.getReason() : "节点执行失败";
                log.warn("群聊规划执行失败：group={}, taskCode={}, round={}, reason={}",
                        targetId, taskCode, round, reason);

                if (round >= maxRounds) {
                    workflowTaskComponent.failByCode(taskCode, reason);
                    return;
                }
                history = toNodeResultRefs(context, reason);
                userContentForReplan = userContent + "\n[上一次执行失败，请重新规划] 失败原因：" + reason;
            }
        } finally {
            clearCancelFlag(msgCode);
        }
    }

    /** 获取群成员 Agent 编码列表（排除发言用户）；为空返回空列表。 */
    private List<String> groupAgentCodes(String userId, String targetId) {
        List<ChatMemberDto> members = chatGroupComponent.getGroupMembers(targetId);
        if (CollectionUtils.isEmpty(members)) {
            return java.util.Collections.emptyList();
        }
        return members.stream()
                .filter(member -> !member.getCode().equalsIgnoreCase(userId))
                .map(ChatMemberDto::getCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    /** 规划一次：调用 planner 生成图，并经 PlanValidator 校验；失败返回 null。 */
    private XyFlow plan(String userId, String targetId, String conversationCode, List<String> agentCodes,
                        String userContent, List<PlanningRequest.NodeResultRef> history, boolean isReplan) {
        try {
            XyFlow xyFlow = workflowPlanner.plan(new PlanningRequest(userContent, agentCodes, history));
            String error = planValidator.validate(xyFlow);
            if (error != null) {
                log.warn("群聊规划校验失败：group={}, error={}", targetId, error);
                return null;
            }
            return xyFlow;
        } catch (Exception e) {
            log.warn("群聊规划异常：group={}, err={}", targetId, e.getMessage(), e);
            return null;
        }
    }

    /** 将执行上下文中的 nodeResults 转成 replan 历史引用，并附上失败原因占位。 */
    private List<PlanningRequest.NodeResultRef> toNodeResultRefs(WorkflowContext context, String failureReason) {
        List<PlanningRequest.NodeResultRef> refs = new java.util.ArrayList<>();
        if (context.getNodeResults() != null) {
            context.getNodeResults().forEach((nodeId, output) ->
                    refs.add(new PlanningRequest.NodeResultRef(nodeId, output)));
        }
        // 追加一条失败原因，提示 planner 上次问题
        refs.add(new PlanningRequest.NodeResultRef("__last_error__", failureReason));
        return refs;
    }
}
