package com.xiaomizhou.dpsk.workflow;

import com.xiaomizhou.dpsk.constant.ConversationType;
import com.xiaomizhou.dpsk.db.WorkflowTaskExecuteComponent;
import com.xiaomizhou.dpsk.db.chat.ImAgentCallback;
import com.xiaomizhou.dpsk.db.dto.WorkflowTaskDto;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.db.model.WorkflowTaskDO;
import com.xiaomizhou.dpsk.utils.SequenceUtils;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartNodeProcessor extends NodeComponent {

    @Override
    public void process() throws Exception {

        WorkflowContext context = this.getContextBean(WorkflowContext.class);
        NodeContext node = context.getNodeContext(getNodeId());

        // will not happen,just for safety
        if (!NodeStep.NODE_TYPE_START.left.equalsIgnoreCase(node.getNodeType())) {
            log.error("start node execute error!nodeId:{}", getNodeId());
            return;
        }

        String taskCode = context.getTaskId();
        String nodeId = node.getNodeId();

        WorkflowTaskExecuteComponent component = context.getWorkflowTaskExecuteComponent();
        WorkflowNodeLogDO nodelog = component.getOne(taskCode, nodeId);
        if (nodelog != null && nodelog.skip()) {
            log.info("start node execute skip!:{}", nodelog.getId());
            return;
        }

        WorkflowTaskDto task = context.getWorkflowTaskComponent().getByCode(context.getTaskId());
        if (WorkflowTaskDO.STATUS_CANCELLED == task.getStatus()) {
            log.info("任务已经取消!");
            return;
        }

        // 开始事件
        Long id = component.start(NodeContext.builder()
                .nodeType(node.getNodeType())
                .nodeId(nodeId)
                .nodeLabel(node.getNodeLabel())
                .build(), taskCode, "", context.getContextData());


        // 不发送任何事件返回前端
        ImAgentCallback callback = new ImAgentCallback(context.getUserId(),
                context.getConversationCode(),
                ConversationType.WORKFLOW.name(),
                context.getTargetId(),
                taskCode,
                context.getChatMessageComponent(),
                context.getTokenUsageDao(),
                context.getAgentDefProvider());

        callback.setStreamCode(SequenceUtils.generator().next("STM"));
        callback.setCancelFlag(context.getCancelFlag());

        // 修改状态
        component.end(id, WorkflowNodeLogDO.STATUS_SUCCESS, null, "", context.getContextData(), null);
        log.info("start node execute!nodeId:{}", getNodeId());
        return;
    }
}
