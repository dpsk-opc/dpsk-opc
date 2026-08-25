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
            endSuccess(wf, node, logId, result.getOutputText(), result.getOutputText());
            log.info("agent node execute!nodeId:{},result:{}", node.getNodeId(), result);
            return NodeExecutionResult.ok();
        }

        // 失败语义：返回 success=false（携带失败原因），由 LangGraph 适配层中断整图并触发 replan；
        // LiteFlow 适配层不检查 success，专家团失败继续跑的行为不变。
        String reason = result.getOutputText() != null ? result.getOutputText() : "Agent 节点执行失败";
        endFailed(wf, logId, reason);
        log.warn("agent node execute failed!nodeId:{},reason:{}", node.getNodeId(), reason);
        return NodeExecutionResult.fail(reason);
    }
}
