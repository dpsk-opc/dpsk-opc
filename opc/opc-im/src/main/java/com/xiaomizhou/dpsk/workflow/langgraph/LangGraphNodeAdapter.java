package com.xiaomizhou.dpsk.workflow.langgraph;

import com.xiaomizhou.dpsk.workflow.NodeContext;
import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutionResult;
import com.xiaomizhou.dpsk.workflow.api.NodeExecutor;
import com.xiaomizhou.dpsk.workflow.node.NodeExecutorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * LangGraph4j node 处理器：把 LangGraph 节点生命周期翻译为对引擎无关
 * {@link NodeExecutor} 的调用，并将路由结果写回 State 供 ConditionalEdge 使用。
 *
 * <p>失败传播：当 {@link NodeExecutionResult#isSuccess()} 为 false（或执行抛异常）时，
 * 以异常终止整图，由 {@link LangGraphWorkflowEngine} 捕获后驱动 replan。
 */
@Slf4j
public class LangGraphNodeAdapter implements AsyncNodeAction<LangGraphState> {

    private final String nodeId;

    public LangGraphNodeAdapter(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(LangGraphState state) {
        try {
            WorkflowContext wf = state.context();
            if (wf == null) {
                throw new IllegalStateException("LangGraphState missing context!");
            }

            NodeContext node = wf.getNodeContext(nodeId);
            if (node == null) {
                throw new IllegalStateException("node config not exist! nodeId:" + nodeId);
            }

            NodeExecutor executor = NodeExecutorRegistry.get(node.getNodeType());
            NodeExecutionResult result = executor.execute(new LangGraphNodeExecutionContext(wf, nodeId));

            // 失败语义：success=false → 抛异常终止整图（携带失败原因，驱动 replan）
            if (result != null && !result.isSuccess()) {
                String reason = result.getError() != null ? result.getError() : "节点执行失败";
                throw new IllegalStateException("node execute failed! nodeId:" + nodeId + ", reason:" + reason);
            }

            // 将路由目标写回 State，供 ConditionalEdge 读取
            Map<String, Object> update = new HashMap<>();
            if (result != null && result.getRouteId() != null) {
                update.put(LangGraphState.ROUTE_KEY, result.getRouteId());
            }
            return CompletableFuture.completedFuture(update);
        } catch (Exception e) {
            log.warn("langgraph node execute error! nodeId:{}, err:{}", nodeId, e.getMessage());
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
