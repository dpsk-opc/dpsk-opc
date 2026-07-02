package com.xiaomizhou.dpsk.workflow.xyflow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 将 XyFlow DAG 图转换为 LiteFlow EL 表达式。
 *
 * <p>EL 语法：
 * <ul>
 *   <li>THEN(a, b, c) — 顺序执行 a, b, c</li>
 *   <li>THEN(a, SWITCH(b).TO(c, d).DEFAULT(x)) — b 分支，走 c/d/x 之一</li>
 * </ul>
 *
 * <p>转换规则（仅基于 edge 的 source/target）：
 * <ul>
 *   <li>节点有 1 条出边 → THEN 串联</li>
 *   <li>节点有 N 条出边 (N > 1) → SWITCH，前 N-1 条进 TO，最后一条进 DEFAULT</li>
 *   <li>遇到已访问节点 → 仅输出 id，不再展开，防止死循环</li>
 *   <li>end 节点 → 直接返回 id</li>
 * </ul>
 */
public class XyFlowToLiteFlowUtils {

    private XyFlowToLiteFlowUtils() {
    }

    /**
     * 将 XyFlow DAG 转换为 LiteFlow EL 表达式。
     */
    public static String toEl(XyFlow xyFlow) {
        if (xyFlow == null || xyFlow.getSteps() == null || xyFlow.getSteps().isEmpty()) {
            return "";
        }

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

        // 3. 找 start 节点
        String startId = stepMap.values().stream()
                .filter(s -> "start".equalsIgnoreCase(s.getType()))
                .map(NodeStep::getId)
                .findFirst()
                .orElse(null);
        if (startId == null) {
            return "";
        }

        // 4. 有效节点 id 集合（只允许 step 中的节点）
        Set<String> validIds = stepMap.keySet();

        // 5. DFS 构建 EL
        Set<String> visited = new HashSet<>();
        return buildChain(startId, stepMap, outEdges, validIds, visited);
    }

    /**
     * 递归构建从 nodeId 开始的 EL 片段。
     * visited 记录已经"展开过"的节点，避免环路死循环。
     */
    private static String buildChain(String nodeId,
                                     Map<String, NodeStep> stepMap,
                                     Map<String, List<NodeEdge>> outEdges,
                                     Set<String> validIds,
                                     Set<String> visited) {

        NodeStep step = stepMap.get(nodeId);
        if (step == null) {
            return "";
        }

        // end 节点直接返回自身
        if ("end".equalsIgnoreCase(step.getType())) {
            return nodeId;
        }

        // 获取出边，只保留 target 在 step 中的边
        List<NodeEdge> edges = outEdges.getOrDefault(nodeId, Collections.emptyList()).stream()
                .filter(e -> validIds.contains(e.getTarget()))
                .collect(Collectors.toList());

        if (edges.isEmpty()) {
            return nodeId;
        }

        // ---- 单条出边：直接串联 ----
        if (edges.size() == 1) {
            NodeEdge edge = edges.get(0);
            String target = edge.getTarget();

            // 环路：目标已展开过，只输出 id
            if (visited.contains(target)) {
                return "THEN(" + nodeId + ", " + target + ").id(\"" + "id_" + nodeId + "\")";
            }

            visited.add(nodeId);
            String next = buildChain(target, stepMap, outEdges, validIds, visited);

            if (next.isEmpty()) {
                return nodeId;
            }
            // 合并 THEN
            if (next.startsWith("THEN(")) {
                return "THEN(" + nodeId + ", " + next.substring(5)+ ".id(\"id_" + nodeId + "\")";
            }
            return "THEN(" + nodeId + ", " + next + ").id(\"" + "id_" + nodeId + "\")";
        }

        // ---- 多条出边：SWITCH 分支 ----
        // 前 N-1 条 → TO，最后一条 → DEFAULT
        // 注意：SWITCH(nodeId) 中已经包含了当前节点，不需要再在 THEN 里重复
        List<NodeEdge> toEdges = edges.subList(0, edges.size() - 1);
        NodeEdge defaultEdge = edges.get(edges.size() - 1);

        // 构建 TO 分支列表（每个目标递归展开）
        List<String> toParts = new ArrayList<>();
        for (NodeEdge e : toEdges) {
            String t = e.getTarget();
            if (visited.contains(t)) {
                toParts.add(t);
            } else {
                String expanded = buildChain(t, stepMap, outEdges, validIds, visited);
                if (!expanded.isEmpty()) {
                    toParts.add(expanded);
                }
            }
        }

        // 构建 DEFAULT 分支
        String defTarget = defaultEdge.getTarget();
        String defPart;
        if (visited.contains(defTarget)) {
            defPart = defTarget;
        } else {
            defPart = buildChain(defTarget, stepMap, outEdges, validIds, visited);
        }

        StringBuilder sw = new StringBuilder();
        // SWITCH 节点自身在 SWITCH(nodeId) 中表达，不再在 THEN 中重复
        sw.append("SWITCH(").append(nodeId).append(")");

        if (!toParts.isEmpty()) {
            sw.append(".TO(").append(String.join(", ", toParts)).append(")");
        }

        if (!defPart.isEmpty()) {
            sw.append(".DEFAULT(").append(defPart).append(")");
        }

        return sw.toString();
    }

}
