package com.xiaomizhou.dpsk.workflow;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.db.WorkflowTaskComponent;
import com.xiaomizhou.dpsk.db.WorkflowTaskExecuteComponent;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HumanConfirmNodeProcessor extends NodeComponent {

    @Override
    public void process() throws Exception {

        String nodeId = getNodeId();
        log.info("human confirm node executed! nodeId:{}", nodeId);

        WorkflowContext context = this.getContextBean(WorkflowContext.class);
        NodeContext node = context.getNodeContext(getNodeId());

        // will not happen,just for safety
        if (!NodeStep.NODE_TYPE_HUMAN_CONFIRM.left.equalsIgnoreCase(node.getNodeType())) {
            log.error("start node execute error!nodeId:{}", getNodeId());
            return;
        }

        String taskCode = context.getTaskId();
        WorkflowTaskExecuteComponent component = context.getWorkflowTaskExecuteComponent();
        WorkflowNodeLogDO nodelog = component.getOne(taskCode, nodeId);
        if (nodelog != null && nodelog.skip()) {
            log.info("start node execute skip!:{}", nodelog.getId());
            return;
        }

        WorkflowTaskComponent workflowTaskComponent = context.getWorkflowTaskComponent();

        WorkflowTaskDto task = workflowTaskComponent.getByCode(taskCode);

        if (WorkflowTaskDO.STATUS_CANCELLED == task.getStatus()) {
            log.info("任务已经取消!");
            return;
        }

        // 开始事件
        Long id = component.start(NodeContext.builder()
                .nodeType(node.getNodeType())
                .nodeId(nodeId)
                .nodeLabel(node.getNodeLabel())
                .build(), taskCode, node.getAgentCode(), task.getContextData());


        // 设置pending
        WorkflowConfirmManager confirmManager = context.getWorkflowConfirmManager();
        confirmManager.requestConfirm(taskCode, nodeId);

        // 节点id，任务编码都传给前端，
        Map<String,Object> meta = Maps.newHashMap();

        meta.put("nodeId", node.getNodeId());
        meta.put("taskCode", task.getCode());
        meta.put("context", task.getContextData());
        meta.put("timeout", 120);
        meta.put("taskName", task.getName());

        // ws通知前端
        WsUtils.send(new WsMessage(WsMsgType.TASK_CONFIRM, meta));

        WorkflowConfirmManager.WorkflowConfirmDto dto = confirmManager.onConform(taskCode, nodeId, TimeUnit.SECONDS, 120);

        // 超时
        if (Objects.isNull(dto)) {
            component.end(id, WorkflowNodeLogDO.STATUS_PENDING, WorkflowTaskDO.STATUS_APPROVING, null, null, "用户超时未确认");
            throw new RuntimeException("用户超时未确认，终止流程");
        }

        // 继续执行
        if (WorkflowConfirmManager.WorkflowConfirmDto.CONFIRM_RESULT_CONFIRM.equals(dto.getConfirmResult())) {

            // 确认端已同意放行，数据已经写到数据库
            WorkflowNodeLogDO log = component.getOne(taskCode, nodeId);
            if (WorkflowNodeLogDO.STATUS_SUCCESS == log.getStatus()) {
                return;
            }

            component.end(id, WorkflowNodeLogDO.STATUS_SUCCESS, null, "", task.getContextData(), dto.getConfirmReason());
            return;
        }

        // 用户终止
        if (WorkflowConfirmManager.WorkflowConfirmDto.CONFIRM_RESULT_REJECT.equals(dto.getConfirmResult())) {
            component.end(id, WorkflowNodeLogDO.TYPE_END, WorkflowTaskDO.STATUS_CANCELLED,"用户拒绝执行", task.getContextData(), dto.getConfirmReason());
            return;
        }

        throw new RuntimeException("未知的状态");
    }
}
