package com.xiaomizhou.dpsk.workflow;

import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.db.ChatMessageComponent;
import com.xiaomizhou.dpsk.db.WorkflowTaskComponent;
import com.xiaomizhou.dpsk.db.WorkflowTaskExecuteComponent;
import com.xiaomizhou.dpsk.db.dao.TokenUsageDao;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeEdge;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 从 {@link XyFlow} 图 + 任务信息构建引擎无关的 {@link WorkflowContext}。
 *
 * <p>由各工作流引擎（LiteFlow / LangGraph4j）在执行前统一调用，与具体引擎解耦。
 */
public final class XyFlowContextBuilder {

    private XyFlowContextBuilder() {
    }

    public static WorkflowContext build(XyFlow xyFlow,
                                        WorkflowTaskDto task,
                                        String userId,
                                        String targetId,
                                        String conversationCode,
                                        String contextData,
                                        AtomicBoolean cancelFlag,
                                        WorkflowConfirmManager confirmManager,
                                        AgentOrchestrator orchestrator,
                                        WorkflowTaskComponent workflowTaskComponent,
                                        ChatMessageComponent chatMessageComponent,
                                        TokenUsageDao tokenUsageDao,
                                        AgentDefProvider agentDefProvider,
                                        WorkflowTaskExecuteComponent workflowTaskExecuteComponent) {

        List<NodeStep> steps = xyFlow.getSteps();

        // 按 agentCode 聚合 mcp / skill / prompt（同一 agent 复用同一份配置）。
        // 注意：配置可能缺失（非 agent 节点 / 未配置 mcp、skill、prompt），
        // 用普通 HashMap 保留 null 语义，Collectors.toMap 遇到 null value 会抛 NPE。
        Map<String, List<String>> mcpCodes = new HashMap<>();
        Map<String, List<String>> skillPaths = new HashMap<>();
        Map<String, String> prompts = new HashMap<>();
        for (NodeStep step : steps) {
            mcpCodes.put(step.getAgentCode(), step.getMcpCodes());
            skillPaths.put(step.getAgentCode(), step.getSkillPaths());
            prompts.put(step.getAgentCode(), step.getSystemPrompt());
        }

        Map<String, NodeContext> nodes = steps.stream()
                .map(step -> NodeContext.builder()
                        .nodeId(step.getId())
                        .nodeType(step.getType())
                        .nodeLabel(step.getLabel())
                        .mcpCodes(mcpCodes.get(step.getAgentCode()))
                        .skillPaths(skillPaths.get(step.getAgentCode()))
                        .prompt(prompts.get(step.getAgentCode()))
                        .agentCode(step.getAgentCode())
                        .chooseNodes(buildConditions(xyFlow, step))
                        .build())
                .collect(Collectors.toMap(NodeContext::getNodeId, Function.identity()));

        return WorkflowContext.builder()
                .userId(userId)
                .targetId(targetId)
                .taskId(task.getCode())
                .nodes(nodes)
                .conversationCode(conversationCode)
                .orchestrator(orchestrator)
                .workflowTaskComponent(workflowTaskComponent)
                .chatMessageComponent(chatMessageComponent)
                .tokenUsageDao(tokenUsageDao)
                .agentDefProvider(agentDefProvider)
                .contextData(contextData)
                .cancelFlag(cancelFlag)
                .workflowTaskExecuteComponent(workflowTaskExecuteComponent)
                .workflowConfirmManager(confirmManager)
                .build();
    }

    /** 构建 switch 节点的条件列表（该节点的所有出边），非 switch 节点返回 null */
    private static List<NodeContext.NodeCondition> buildConditions(XyFlow xyFlow, NodeStep step) {
        if (!xyFlow.isSwitchNode(step.getId())) {
            return null;
        }
        List<NodeContext.NodeCondition> conditions = Lists.newArrayList();
        for (NodeEdge edge : xyFlow.getEdges()) {
            if (step.getId().equalsIgnoreCase(edge.getSource())) {
                NodeContext.NodeCondition condition = new NodeContext.NodeCondition();
                condition.setCondition(edge.getCondition());
                condition.setConditionLabel(edge.getLabel());
                condition.setNextNodeId(edge.getTarget());
                conditions.add(condition);
            }
        }
        return conditions;
    }
}
