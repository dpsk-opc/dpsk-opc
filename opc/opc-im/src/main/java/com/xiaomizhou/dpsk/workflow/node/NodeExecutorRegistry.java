package com.xiaomizhou.dpsk.workflow.node;

import com.xiaomizhou.dpsk.workflow.api.NodeExecutor;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 节点类型 → 执行器 注册表。
 *
 * <p>新增节点类型时，只需实现 {@link NodeExecutor} 并在此注册；
 * LiteFlow / LangGraph4j 适配层统一通过 {@link #get(String)} 获取执行器，无需感知具体类型。
 */
public final class NodeExecutorRegistry {

    private static final Map<String, NodeExecutor> EXECUTORS;

    static {
        Map<String, NodeExecutor> map = new HashMap<>();
        map.put(NodeStep.NODE_TYPE_START.left, new StartNodeExecutor());
        map.put(NodeStep.NODE_TYPE_COMMON_AGENT.left, new AgentNodeExecutor());
        map.put(NodeStep.NODE_TYPE_SWITCH.left, new SwitchNodeExecutor());
        map.put(NodeStep.NODE_TYPE_HUMAN_CONFIRM.left, new HumanConfirmNodeExecutor());
        map.put(NodeStep.NODE_TYPE_END.left, new EndNodeExecutor());
        EXECUTORS = Collections.unmodifiableMap(map);
    }

    private NodeExecutorRegistry() {
    }

    /** 根据节点类型获取执行器 */
    public static NodeExecutor get(String nodeType) {
        NodeExecutor executor = EXECUTORS.get(nodeType);
        if (executor == null) {
            throw new IllegalStateException("未注册节点类型的执行器: " + nodeType);
        }
        return executor;
    }
}
