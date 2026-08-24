package com.xiaomizhou.dpsk.workflow.node;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowConfirmManager;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 人工确认节点执行器：发起 WS 确认请求并等待用户确认 / 拒绝 / 超时。
 */
@Slf4j
public class HumanConfirmNodeExecutor extends AbstractNodeExecutor {

    /** 确认等待超时（秒） */
    private static final int CONFIRM_TIMEOUT_SECONDS = 120;

    @Override
    protected void validateType(NodeContext node) {
        if (!NodeStep.NODE_TYPE_HUMAN_CONFIRM.left.equalsIgnoreCase(node.getNodeType())) {
            throw new IllegalStateException("confirm node execute error!nodeId:" + node.getNodeId());
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowContext wf, NodeContext node, WorkflowTaskDto task, NodeExecutionContext ctx) throws Exception {
        Long logId = startLog(wf, node, node.getAgentCode(), task.getContextData());

        WorkflowConfirmManager confirmManager = wf.getWorkflowConfirmManager();
        confirmManager.requestConfirm(wf.getTaskId(), node.getNodeId());

        // 节点id、任务编码都传给前端
        Map<String, Object> meta = Maps.newHashMap();
        meta.put("nodeId", node.getNodeId());
        meta.put("taskCode", task.getCode());
        meta.put("context", task.getContextData());
        meta.put("timeout", CONFIRM_TIMEOUT_SECONDS);
        meta.put("taskName", task.getName());

        // ws 通知前端
        WsUtils.send(new WsMessage(WsMsgType.TASK_CONFIRM, meta));

        WorkflowConfirmManager.WorkflowConfirmDto dto = confirmManager.onConform(wf.getTaskId(), node.getNodeId(), TimeUnit.SECONDS, CONFIRM_TIMEOUT_SECONDS);

        // 超时
        if (Objects.isNull(dto)) {
            endTask(wf, logId, WorkflowNodeLogDO.STATUS_PENDING, WorkflowTaskDO.STATUS_APPROVING, null, null, "用户超时未确认");
            throw new RuntimeException("用户超时未确认，终止流程");
        }

        // 确认继续执行
        if (WorkflowConfirmManager.WorkflowConfirmDto.CONFIRM_RESULT_CONFIRM.equals(dto.getConfirmResult())) {
            // 确认端已同意放行，数据已写入数据库
            WorkflowNodeLogDO nodeLog = wf.getWorkflowTaskExecuteComponent().getOne(wf.getTaskId(), node.getNodeId());
            if (WorkflowNodeLogDO.STATUS_SUCCESS == nodeLog.getStatus()) {
                return NodeExecutionResult.ok();
            }
            endSuccess(wf, logId, task.getContextData(), dto.getConfirmReason());
            return NodeExecutionResult.ok();
        }

        // 用户终止
        if (WorkflowConfirmManager.WorkflowConfirmDto.CONFIRM_RESULT_REJECT.equals(dto.getConfirmResult())) {
            endTask(wf, logId, WorkflowNodeLogDO.TYPE_END, WorkflowTaskDO.STATUS_CANCELLED, "用户拒绝执行", task.getContextData(), dto.getConfirmReason());
            return NodeExecutionResult.ok();
        }

        throw new RuntimeException("未知的状态");
    }
}
