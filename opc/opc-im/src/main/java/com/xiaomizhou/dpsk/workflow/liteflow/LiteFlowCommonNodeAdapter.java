package com.xiaomizhou.dpsk.workflow.liteflow;

import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutor;
import com.xiaomizhou.dpsk.workflow.node.NodeExecutorRegistry;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * LiteFlow 通用节点适配器：把 LiteFlow 的 {@link #process()} 生命周期翻译为对引擎无关
 * {@link NodeExecutor} 的调用。start / agent / confirm / end 等普通节点统一注册为本适配器。
 */
@Slf4j
public class LiteFlowCommonNodeAdapter extends NodeComponent {

    @Override
    public void process() throws Exception {
        log.info("node executed! nodeId:{}", getNodeId());

        WorkflowContext wf = getContextBean(WorkflowContext.class);
        NodeContext node = wf.getNodeContext(getNodeId());
        NodeExecutor executor = NodeExecutorRegistry.get(node.getNodeType());
        executor.execute(new LiteFlowNodeExecutionContext(this, wf));
    }
}
