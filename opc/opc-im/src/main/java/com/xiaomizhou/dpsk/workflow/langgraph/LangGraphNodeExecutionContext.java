package com.xiaomizhou.dpsk.workflow.langgraph;

import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionContext;

/**
 * LangGraph4j 适配层的 {@link NodeExecutionContext} 实现，
 * 屏蔽 LangGraph 状态访问细节，供引擎无关的 {@link com.xiaomizhou.dpsk.workflow.api.NodeExecutor} 使用。
 */
public class LangGraphNodeExecutionContext implements NodeExecutionContext {

    private final WorkflowContext wf;
    private final String nodeId;

    public LangGraphNodeExecutionContext(WorkflowContext wf, String nodeId) {
        this.wf = wf;
        this.nodeId = nodeId;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public WorkflowContext getContext() {
        return wf;
    }

    @Override
    public NodeContext getNode() {
        return wf.getNodeContext(nodeId);
    }
}
