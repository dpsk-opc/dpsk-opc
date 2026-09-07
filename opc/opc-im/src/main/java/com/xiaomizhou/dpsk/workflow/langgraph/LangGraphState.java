package com.xiaomizhou.dpsk.workflow.langgraph;

import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

/**
 * LangGraph4j 引擎的状态（State）实现。
 *
 * <p>channel 中持有引擎无关的 {@link WorkflowContext}（含 nodeResults / currentNodeId），
 * 以及 switch 节点路由所需的 routeId。
 *
 * <p>构造/更新均为轻量拷贝，避免对业务对象做 Java 深度序列化（见 {@link LightStateSerializer}）。
 */
public class LangGraphState extends AgentState {

    /** 状态中持有 WorkflowContext 的 key */
    public static final String CONTEXT_KEY = "context";

    /** 状态中持有 switch 节点路由目标（routeId）的 key */
    public static final String ROUTE_KEY = "routeId";

    public LangGraphState(Map<String, Object> initData) {
        super(initData);
    }

    /**
     * 获取任务级上下文。
     */
    public WorkflowContext context() {
        return this.<WorkflowContext>value(CONTEXT_KEY).orElse(null);
    }
}
