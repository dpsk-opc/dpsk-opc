package com.xiaomizhou.dpsk.workflow.langgraph;

import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import java.util.Map;
import java.util.Optional;

/**
 * LangGraph4j 工作流引擎：负责把 {@link XyFlow} 图翻译为 StateGraph 并同步执行。
 *
 * <p>仅此层依赖 LangGraph4j API；节点业务逻辑全部在引擎无关的
 * {@link com.xiaomizhou.dpsk.workflow.api.NodeExecutor} 中。
 *
 * <p>失败传播：节点执行失败（异常 / success=false）会以异常终止整图，
 * 本引擎捕获后返回 {@link ExecutionResult}（含失败节点 id 与原因），由上层驱动 replan。
 */
@Slf4j
public class LangGraphWorkflowEngine {

    /**
     * 执行结果：成功 / 失败信息（含失败节点 id 与原因）。
     */
    public static class ExecutionResult {
        private final boolean success;
        private final String failedNodeId;
        private final String reason;

        private ExecutionResult(boolean success, String failedNodeId, String reason) {
            this.success = success;
            this.failedNodeId = failedNodeId;
            this.reason = reason;
        }

        public static ExecutionResult ok() {
            return new ExecutionResult(true, null, null);
        }

        public static ExecutionResult fail(String failedNodeId, String reason) {
            return new ExecutionResult(false, failedNodeId, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailedNodeId() {
            return failedNodeId;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 构建并执行工作流。
     *
     * @param xyFlow  planner 输出的 XyFlow 图
     * @param context 由 {@code XyFlowContextBuilder} 构建的任务上下文
     * @return 执行结果；节点失败时 success=false 并携带失败节点 id 与原因
     */
    public ExecutionResult execute(XyFlow xyFlow, WorkflowContext context) {
        try {
            // 1. 翻译 XyFlow → StateGraph
            StateGraph<LangGraphState> graph = XyFlowToLangGraphUtils.buildGraph(xyFlow, context);

            // 2. 编译并执行（同步 invoke）
            CompiledGraph<LangGraphState> compiled = graph.compile();
            Optional<LangGraphState> result = compiled.invoke(Map.of(LangGraphState.CONTEXT_KEY, context));

            log.info("langgraph workflow executed. task:{}, done:{}", context.getTaskId(), result.isPresent());
            return ExecutionResult.ok();
        } catch (Exception e) {
            // 节点失败由 LangGraphNodeAdapter 以异常终止图，这里汇总失败信息
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String failedNodeId = extractFailedNodeId(msg);
            log.warn("langgraph workflow failed. task:{}, failedNode:{}, err:{}",
                    context.getTaskId(), failedNodeId, msg);
            return ExecutionResult.fail(failedNodeId, msg);
        }
    }

    /**
     * 从异常消息中提取失败节点 id（LangGraphNodeAdapter 的异常消息格式为
     * "node execute failed! nodeId:xxx, ..." / "langgraph node execute error! nodeId:xxx, ..."）。
     */
    private String extractFailedNodeId(String message) {
        if (message == null) {
            return null;
        }
        int idx = message.indexOf("nodeId:");
        if (idx < 0) {
            return null;
        }
        String rest = message.substring(idx + "nodeId:".length()).trim();
        int comma = rest.indexOf(',');
        return comma > 0 ? rest.substring(0, comma).trim() : rest;
    }
}
