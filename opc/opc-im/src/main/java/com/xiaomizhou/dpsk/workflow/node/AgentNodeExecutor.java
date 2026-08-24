package com.xiaomizhou.dpsk.workflow.node;

import com.xiaomizhou.dpsk.agent.PipelineResult;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.support.AgentInvoker;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import lombok.extern.slf4j.Slf4j;

/**
 * 普通 Agent 节点执行器：调用 Agent 处理当前上下文，结果作为后续节点的输入。
 */
@Slf4j
public class AgentNodeExecutor extends AbstractNodeExecutor {

    @Override
    protected void validateType(NodeContext node) {
        if (!NodeStep.NODE_TYPE_COMMON_AGENT.left.equalsIgnoreCase(node.getNodeType())) {
            throw new IllegalStateException("agent node execute error!nodeId:" + node.getNodeId());
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowContext wf, NodeContext node, WorkflowTaskDto task, NodeExecutionContext ctx) {
        Long logId = startLog(wf, node, node.getAgentCode(), task.getContextData());

        PipelineResult result = AgentInvoker.invoke(wf, node, task.getContextData(), node.getAgentCode());
        if (result.isSuccess()) {
            endSuccess(wf, logId, result.getOutputText(), result.getOutputText());
        } else {
            // 失败时继续沿用原上下文，重试时使用上一次的 contextData
            endFailed(wf, logId, result.getOutputText());
        }

        log.info("agent node execute!nodeId:{},result:{}", node.getNodeId(), result);
        return NodeExecutionResult.ok();
    }
}
