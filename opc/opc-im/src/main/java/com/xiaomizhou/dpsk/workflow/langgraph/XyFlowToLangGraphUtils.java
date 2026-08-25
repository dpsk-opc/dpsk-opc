package com.xiaomizhou.dpsk.workflow.langgraph;

import com.xiaomizhou.dpsk.workflow.WorkflowContext;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeEdge;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 将 XyFlow DAG 图翻译为 langgraph4j {@link StateGraph}。
 *
 * <p>转换规则（仅基于 edge 的 source/target）：
 * <ul>
 *   <li>每个 step 注册为一个 node（含 start / end，LangGraph 无 START/END 特殊节点）；</li>
 *   <li>从 {@link StateGraph#START} 连到 start 节点；</li>
 *   <li>单出边 → {@code graph.edge(from, to)}；</li>
 *   <li>switch 多出边 → {@code graph.conditionalEdge(from, state -> routeId, Map.of(routeId → target))}；</li>
 *   <li>end 节点 → 连到 {@link StateGraph#END}，作为图终止点。</li>
 * </ul>
 */
public final class XyFlowToLangGraphUtils {

    private XyFlowToLangGraphUtils() {
    }

    /**
     * 将 XyFlow 转换为 langgraph4j StateGraph。
     *
     * @param xyFlow 前端画布模型（已通过 PlanValidator 校验）
     * @param context 任务上下文（注入 nodeResults，供 replan 使用）
     */
    public static StateGraph<LangGraphState> buildGraph(XyFlow xyFlow, WorkflowContext context) {
        try {
            return buildGraphInternal(xyFlow, context);
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            throw new IllegalStateException("XyFlow to StateGraph translate failed!", e);
        }
    }

    private static StateGraph<LangGraphState> buildGraphInternal(XyFlow xyFlow, WorkflowContext context)
            throws org.bsc.langgraph4j.GraphStateException {
        // 1. nodeId -> NodeStep
        Map<String, NodeStep> stepMap = xyFlow.getSteps().stream()
                .collect(Collectors.toMap(NodeStep::getId, s -> s, (a, b) -> a, LinkedHashMap::new));

        // 2. 邻接表 source -> List<NodeEdge>
        Map<String, List<NodeEdge>> outEdges = new LinkedHashMap<>();
        if (xyFlow.getEdges() != null) {
            for (NodeEdge edge : xyFlow.getEdges()) {
                outEdges.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
            }
        }

        // 3. 构建图（序列化器持有执行期共享的 WorkflowContext）
        StateGraph<LangGraphState> graph = new StateGraph<>(new LightStateSerializer(context));

        // 注册所有节点（含 start / end）
        for (NodeStep step : stepMap.values()) {
            graph.addNode(step.getId(), new LangGraphNodeAdapter(step.getId()));
        }

        // 4. 起点：START → start 节点
        String startId = stepMap.values().stream()
                .filter(s -> NodeStep.NODE_TYPE_START.left.equalsIgnoreCase(s.getType()))
                .map(NodeStep::getId)
                .findFirst()
                .orElse(null);
        if (startId == null) {
            throw new IllegalStateException("XyFlow missing start node!");
        }
        graph.addEdge(StateGraph.START, startId);

        // 5. 逐节点连边
        for (NodeStep step : stepMap.values()) {
            List<NodeEdge> edges = outEdges.getOrDefault(step.getId(), List.of());

            // end 节点：连到 END 终止图
            if (NodeStep.NODE_TYPE_END.left.equalsIgnoreCase(step.getType())) {
                graph.addEdge(step.getId(), StateGraph.END);
                continue;
            }

            if (edges.size() == 1) {
                graph.addEdge(step.getId(), edges.get(0).getTarget());
            } else if (edges.size() > 1) {
                // switch 多出边 → conditional edge
                Map<String, String> mappings = new HashMap<>();
                for (NodeEdge edge : edges) {
                    mappings.put(routeKey(edge.getTarget()), edge.getTarget());
                }
                AsyncEdgeAction<LangGraphState> condition = state -> CompletableFuture.completedFuture(
                        state.<String>value(LangGraphState.ROUTE_KEY).orElse(""));
                graph.addConditionalEdges(step.getId(), condition, mappings);
            }
            // edges.size() == 0 且非 end：理论上校验已拒绝（连通性），此处忽略
        }

        return graph;
    }

    /**
     * 计算 switch 路由值（routeId）对应的图节点 id 映射键。
     *
     * <p>SwitchNodeExecutor 返回 {@code "id_" + 目标节点id}，因此映射键需与之对齐；
     * 若目标 id 本身已带 {@code id_} 前缀则不再叠加，保证两种情况都能正确路由。
     */
    private static String routeKey(String targetId) {
        return targetId != null && targetId.startsWith("id_") ? targetId : "id_" + targetId;
    }
}
