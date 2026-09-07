package com.xiaomizhou.dpsk.workflow.api;

import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;

/**
 * 节点执行上下文：屏蔽引擎差异（LiteFlow 的 getContextBean / getNodeId 等），
 * 由引擎适配层实现并提供给 {@link NodeExecutor}。
 */
public interface NodeExecutionContext {

    /** 当前执行节点的唯一标识 */
    String getNodeId();

    /** 任务级上下文（引擎无关） */
    WorkflowContext getContext();

    /** 当前节点配置，不存在时返回 null */
    NodeContext getNode();
}
