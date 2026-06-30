package com.xiaomizhou.dpsk.workflow;

import com.xiaomizhou.dpsk.db.WorkflowTaskExecuteComponent;
import com.xiaomizhou.dpsk.db.model.WorkflowNodeLogDO;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndNodeProcessor extends NodeComponent {

    @Override
    public void process() throws Exception {
        log.info("start node execute!nodeId:{}", getNodeId());

        WorkflowContext context = this.getContextBean(WorkflowContext.class);
        NodeContext node = context.getNodeContext(getNodeId());

        WorkflowTaskExecuteComponent component = context.getWorkflowTaskExecuteComponent();


        // 开始事件
        Long id = component.start(NodeContext.builder()
                .nodeType(NodeStep.NODE_TYPE_END.left)
                .nodeId(node.getNodeId())
                .nodeLabel(node.getNodeLabel())
                .build(), context.getTargetId(), "", null);

        component.end(id, WorkflowNodeLogDO.STATUS_SUCCESS, "", null);
        log.info("end node execute!nodeId:{}", getNodeId());
    }

}
