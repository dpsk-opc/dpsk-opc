package com.xiaomizhou.dpsk.workflow.node;

import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;

/**
 * 结束节点执行器：任务成功收尾。
 */
@Slf4j
public class EndNodeExecutor extends AbstractNodeExecutor {

    @Override
    protected boolean isSkipEnabled() {
        return false;
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowContext wf, NodeContext node, WorkflowTaskDto task, NodeExecutionContext ctx) {
        Long logId = startLog(wf, node, "", null);
        endTask(wf, logId, WorkflowNodeLogDO.STATUS_SUCCESS, WorkflowTaskDO.STATUS_SUCCESS, "", null, null);
        log.info("end node execute!nodeId:{}", node.getNodeId());
        return NodeExecutionResult.ok();
    }
}
