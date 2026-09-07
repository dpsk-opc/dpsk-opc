package com.xiaomizhou.dpsk.workflow.node;

import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import lombok.extern.slf4j.Slf4j;

/**
 * 开始节点执行器：记录开始节点日志，并把初始上下文透传给后续节点。
 */
@Slf4j
public class StartNodeExecutor extends AbstractNodeExecutor {

    @Override
    protected void validateType(NodeContext node) {
        if (!NodeStep.NODE_TYPE_START.left.equalsIgnoreCase(node.getNodeType())) {
            throw new IllegalStateException("start node execute error!nodeId:" + node.getNodeId());
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowContext wf, NodeContext node, WorkflowTaskDto task, NodeExecutionContext ctx) {
        Long logId = startLog(wf, node, "", wf.getContextData());
        endSuccess(wf, node, logId, wf.getContextData(), null);
        log.info("start node execute!nodeId:{}", node.getNodeId());
        return NodeExecutionResult.ok();
    }
}
