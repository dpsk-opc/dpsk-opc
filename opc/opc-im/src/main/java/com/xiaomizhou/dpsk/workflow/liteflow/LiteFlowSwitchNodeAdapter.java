package com.xiaomizhou.dpsk.workflow.liteflow;

import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutor;
import com.xiaomizhou.dpsk.workflow.node.NodeExecutorRegistry;
import com.yomahub.liteflow.core.NodeSwitchComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * LiteFlow 分支节点适配器：把 {@link NodeSwitchComponent#processSwitch()} 的路由语义翻译为
 * {@link NodeExecutor} 执行结果的 routeId。
 */
@Slf4j
public class LiteFlowSwitchNodeAdapter extends NodeSwitchComponent {

    @Override
    public String processSwitch() throws Exception {
        log.info("switch node execute!nodeId:{}", getNodeId());

        WorkflowContext wf = getContextBean(WorkflowContext.class);
        NodeContext node = wf.getNodeContext(getNodeId());
        NodeExecutor executor = NodeExecutorRegistry.get(node.getNodeType());
        NodeExecutionResult result = executor.execute(new LiteFlowNodeExecutionContext(this, wf));
        return result == null ? null : result.getRouteId();
    }
}
