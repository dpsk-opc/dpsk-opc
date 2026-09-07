package com.xiaomizhou.dpsk.workflow.liteflow;

import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;
import com.yomahub.liteflow.core.NodeComponent;

/**
 * LiteFlow 适配层的 {@link NodeExecutionContext} 实现，屏蔽 getContextBean / getNodeId 等引擎 API。
 */
public class LiteFlowNodeExecutionContext implements NodeExecutionContext {

    private final NodeComponent adapter;
    private final WorkflowContext wf;

    public LiteFlowNodeExecutionContext(NodeComponent adapter, WorkflowContext wf) {
        this.adapter = adapter;
        this.wf = wf;
    }

    @Override
    public String getNodeId() {
        return adapter.getNodeId();
    }

    @Override
    public WorkflowContext getContext() {
        return wf;
    }

    @Override
    public NodeContext getNode() {
        return wf.getNodeContext(getNodeId());
    }
}
