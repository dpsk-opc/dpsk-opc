package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.workflow.xyflow.NodeEdge;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规划图校验器：对 planner 输出的 XyFlow 做结构性校验（§5.4）。
 *
 * <p>校验规则：1 start + 1 end、start 无入边、end 无出边、无环、
 * switch 出边 ≥ 2、非 switch 出边 ≤ 1、任意两节点连通。
 */
@Component
public class PlanValidator {

    /**
     * 校验 XyFlow。
     *
     * @return 合法返回 null；不合法返回错误描述
     */
    public String validate(XyFlow xyFlow) {
        if (xyFlow == null) {
            return "planner 输出为空";
        }
        List<NodeStep> steps = xyFlow.getSteps();
        List<NodeEdge> edges = xyFlow.getEdges();
        if (steps == null || steps.isEmpty()) {
            return "planner 未输出任何节点";
        }
        if (edges == null) {
            return "planner 未输出任何边";
        }

        // 1. 恰好 1 个 start、1 个 end
        long startCount = steps.stream().filter(s -> NodeStep.NODE_TYPE_START.left.equalsIgnoreCase(s.getType())).count();
        long endCount = steps.stream().filter(s -> NodeStep.NODE_TYPE_END.left.equalsIgnoreCase(s.getType())).count();
        if (startCount != 1) {
            return "必须恰好包含 1 个 start 节点，当前=" + startCount;
        }
        if (endCount != 1) {
            return "必须恰好包含 1 个 end 节点，当前=" + endCount;
        }

        Set<String> ids = steps.stream().map(NodeStep::getId).collect(Collectors.toSet());

        // 2. 边引用有效性、start 无入边、end 无出边
        Set<String> startIds = steps.stream()
                .filter(s -> NodeStep.NODE_TYPE_START.left.equalsIgnoreCase(s.getType()))
                .map(NodeStep::getId).collect(Collectors.toSet());
        Set<String> endIds = steps.stream()
                .filter(s -> NodeStep.NODE_TYPE_END.left.equalsIgnoreCase(s.getType()))
                .map(NodeStep::getId).collect(Collectors.toSet());

        for (NodeEdge edge : edges) {
            if (edge.getSource() == null || edge.getTarget() == null) {
                return "存在边缺少 source/target";
            }
            if (!ids.contains(edge.getSource())) {
                return "边的 source 引用了不存在的节点: " + edge.getSource();
            }
            if (!ids.contains(edge.getTarget())) {
                return "边的 target 引用了不存在的节点: " + edge.getTarget();
            }
            if (endIds.contains(edge.getSource())) {
                return "end 节点不允许有出边";
            }
            if (startIds.contains(edge.getTarget())) {
                return "start 节点不允许有入边";
            }
        }

        // 3. 出边数量约束 + 环检测 + 连通性
        java.util.Map<String, java.util.List<String>> out = new java.util.HashMap<>();
        for (NodeEdge edge : edges) {
            out.computeIfAbsent(edge.getSource(), k -> new java.util.ArrayList<>()).add(edge.getTarget());
        }
        java.util.Map<String, java.util.List<String>> in = new java.util.HashMap<>();
        for (NodeEdge edge : edges) {
            in.computeIfAbsent(edge.getTarget(), k -> new java.util.ArrayList<>()).add(edge.getSource());
        }

        for (NodeStep step : steps) {
            java.util.List<String> outs = out.getOrDefault(step.getId(), List.of());
            if (xyFlow.isSwitchNode(step.getId())) {
                if (outs.size() < 2) {
                    return "switch 节点必须至少 2 条出边: " + step.getId();
                }
            } else {
                if (outs.size() > 1) {
                    return "非 switch 节点不允许有多个出边: " + step.getId();
                }
            }
        }

        // 4. 从 start 出发 BFS 检测环与连通性
        String startId = startIds.iterator().next();
        Set<String> visited = new HashSet<>();
        Set<String> onPath = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(startId);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            if (onPath.contains(cur)) {
                return "检测到环，节点: " + cur;
            }
            if (visited.contains(cur)) {
                continue;
            }
            visited.add(cur);
            onPath.add(cur);
            for (String next : out.getOrDefault(cur, List.of())) {
                if (next.equals(cur) || onPath.contains(next)) {
                    return "检测到环，节点: " + next;
                }
                stack.push(next);
            }
            onPath.remove(cur);
        }

        // 5. 连通性：所有非 start/end 节点均应可达
        Set<String> reachable = visited;
        for (NodeStep step : steps) {
            if (!startIds.contains(step.getId()) && !reachable.contains(step.getId())) {
                return "存在不可达节点: " + step.getId();
            }
        }

        return null;
    }
}
