package com.xiaomizhou.dpsk.workflow.node;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.support.AgentInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;

/**
 * 分支（switch）节点执行器：调用 Agent 选择下一跳节点，通过 routeId 表达路由。
 */
@Slf4j
public class SwitchNodeExecutor extends AbstractNodeExecutor {

    @Override
    protected NodeExecutionResult doExecute(WorkflowContext wf, NodeContext node, WorkflowTaskDto task, NodeExecutionContext ctx) {
        List<NodeContext.NodeCondition> conditions = node.getChooseNodes();
        if (CollectionUtils.isEmpty(conditions)) {
            throw new RuntimeException("Switch node conditions can not be null.");
        }

        Long logId = startLog(wf, node, node.getAgentCode(), task.getContextData());

        PipelineResult result = AgentInvoker.invoke(wf, node, toChoosePrompt(conditions, task.getContextData()), node.getAgentCode());
        if (!result.isSuccess()) {
            // 失败继续用原始的 contextData，重试时使用上一次的 contextData
            endFailed(wf, logId, result.getOutputText());
            throw new RuntimeException("任务执行失败");
        }

        String next = "id_%s".formatted(result.getOutputText());
        endSuccess(wf, logId, task.getContextData(), next);
        return NodeExecutionResult.route(next);
    }

    @Override
    protected NodeExecutionResult onSkip(WorkflowContext wf, NodeContext node, WorkflowNodeLogDO nodeLog) {
        // 输出下一个节点的 id
        return NodeExecutionResult.route(nodeLog.getOutputData());
    }

    @Override
    protected NodeExecutionResult onCancelled(WorkflowContext wf, NodeContext node, WorkflowTaskDto task) {
        throw new RuntimeException("任务已经取消");
    }

    private String toChoosePrompt(List<NodeContext.NodeCondition> conditions, String taskContext) {
        List<String> infos = Lists.newArrayList();
        String template = "nodeId:%s, condition:%s;";
        for (NodeContext.NodeCondition condition : conditions) {
            infos.add(template.formatted(condition.getNextNodeId(), condition.getCondition()));
        }

        return """
                你没有任何背景知识，你需要做的是把用户输入根据条件选择下一个节点。注意：只能返回一个节点id，而且不需要返回任何额外的描述。
                用户输入：%s
                节点和条件：
                %s
                """.formatted(taskContext, Joiner.on(",").join(infos));
    }
}
