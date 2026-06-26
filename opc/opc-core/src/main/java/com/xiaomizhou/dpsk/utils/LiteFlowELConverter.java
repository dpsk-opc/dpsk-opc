package com.xiaomizhou.dpsk.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.JsonArray;
import org.apache.commons.collections.CollectionUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LiteFlow EL 表达式生成器，同时提供节点元数据。
 */
public class LiteFlowELConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ---------- 数据模型 ----------
    static class Step {
        String id;
        String type;
        String label;
        int order;
        String agentCode;
        String agentName;
        String agentAvatar;
        String agentModel;
        List<String> mcpCodes;
        List<String> skillPaths;
        String description;
    }

    static class Edge {
        String source;
        String target;
        String condition;
        String label;
        Integer maxIterations;
    }

    /**
     * 节点详细信息（对外输出）
     */
    public static class NodeDetail {
        public String id;
        public String type;
        public String label;
        public int order;
        public String agentCode;
        public String agentName;
        public String agentAvatar;
        public String agentModel;
        public List<String> mcpCodes;
        public List<String> skillPaths;
        public String description;
        public List<OutEdge> outEdges = new ArrayList<>(); // 出边信息

        public boolean isStartNode() {
            return "start".equals(type);
        }

        public boolean isEndNode() {
            return "end".equals(type);
        }

        public boolean isLoopNode() {
            return CollectionUtils.isNotEmpty(outEdges) && outEdges.stream().anyMatch(e -> e.maxIterations != null);
        }

        public boolean isSwitchNode() {
            return CollectionUtils.isNotEmpty(outEdges) && outEdges.stream().anyMatch(e -> e.condition != null);
        }

        public static class OutEdge {
            public String targetId;
            public String condition;
            public Integer maxIterations; // 循环次数，若有
        }
    }

    /**
     * 转换结果
     */
    public static class ConversionResult {
        public String elExpression;
        public List<NodeDetail> nodeDetails;
    }

    // ---------- 内部数据 ----------
    private final Map<String, Step> allSteps = new HashMap<>();
    private final Map<String, List<Edge>> outEdgesMap = new HashMap<>();

    // ---------- 公共方法 ----------
    /**
     * 仅生成 EL 表达式（兼容旧版本）
     */
    public String convert(JsonNode root) {
        return convertFull(root).elExpression;
    }

    /**
     * 生成 EL 表达式并返回节点详细信息
     */
    public ConversionResult convertFull(JsonNode root) {
        // 1. 解析节点和边
        parseNodes(root);
        parseEdges(root);

        // 2. 构建节点详情列表
        List<NodeDetail> details = buildNodeDetails();

        // 3. 构建 EL 表达式
        String startId = findStartId();
        String el = buildChain(startId);
        // 若 EL 为空，则只保留开始结束
        if (el.isEmpty()) {
            el = "";
        }
        String fullEl = el;

        ConversionResult result = new ConversionResult();
        result.elExpression = fullEl;
        result.nodeDetails = details;
        return result;
    }

    // ---------- 解析 ----------
    private void parseNodes(JsonNode root) {
        ArrayNode stepsNode = (ArrayNode) root.get("steps");
        for (JsonNode s : stepsNode) {
            Step step = new Step();
            step.id = s.get("id").asText();
            step.type = s.get("type").asText();
            step.label = s.get("label").asText();
            step.order = s.get("order").asInt();
            step.agentCode = s.has("agentCode") ? s.get("agentCode").asText() : null;
            step.agentName = s.has("agentName") ? s.get("agentName").asText() : null;
            step.agentAvatar = s.has("agentAvatar") ? s.get("agentAvatar").asText() : null;
            step.agentModel = s.has("agentModel") ? s.get("agentModel").asText() : null;
            step.mcpCodes = s.has("mcpCodes") ? parseStringList(s.get("mcpCodes")) : Collections.emptyList();
            step.skillPaths = s.has("skillPaths") ? parseStringList(s.get("skillPaths")) : Collections.emptyList();
            step.description = s.has("description") ? s.get("description").asText() : null;
            allSteps.put(step.id, step);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) return Collections.emptyList();
        List<String> list = new ArrayList<>();


        for (JsonNode n : node) {
            list.add(n.asText());
        }
        return list;
    }

    private void parseEdges(JsonNode root) {
        ArrayNode edgesNode = (ArrayNode) root.get("edges");
        for (JsonNode e : edgesNode) {
            Edge edge = new Edge();
            edge.source = e.get("source").asText();
            edge.target = e.get("target").asText();
            edge.condition = e.has("condition") ? e.get("condition").asText() : null;
            edge.label = e.has("label") ? e.get("label").asText() : "";
            edge.maxIterations = e.has("maxIterations") ? e.get("maxIterations").asInt() : null;
            outEdgesMap.computeIfAbsent(edge.source, k -> new ArrayList<>()).add(edge);
        }
    }

    private String findStartId() {
        return allSteps.values().stream()
                .filter(s -> "start".equals(s.type))
                .map(s -> s.id)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到 start 节点"));
    }

    // ---------- 构建节点详情 ----------
    private List<NodeDetail> buildNodeDetails() {
        List<NodeDetail> details = new ArrayList<>();
        for (Step step : allSteps.values()) {
            NodeDetail detail = new NodeDetail();
            detail.id = step.id;
            detail.type = step.type;
            detail.label = step.label;
            detail.order = step.order;
            detail.agentCode = step.agentCode;
            detail.agentName = step.agentName;
            detail.agentAvatar = step.agentAvatar;
            detail.agentModel = step.agentModel;
            detail.mcpCodes = step.mcpCodes != null ? new ArrayList<>(step.mcpCodes) : Collections.emptyList();
            detail.skillPaths = step.skillPaths != null ? new ArrayList<>(step.skillPaths) : Collections.emptyList();
            detail.description = step.description;

            // 添加出边
            List<Edge> edges = outEdgesMap.getOrDefault(step.id, Collections.emptyList());
            for (Edge e : edges) {
                NodeDetail.OutEdge out = new NodeDetail.OutEdge();
                out.targetId = e.target;
                out.condition = e.condition;
                out.maxIterations = e.maxIterations;
                detail.outEdges.add(out);
            }
            details.add(detail);
        }
        // 按 order 排序
        details.sort(Comparator.comparingInt(d -> d.order));
        return details;
    }

    // ---------- 构建 EL 表达式 ----------
    private String buildChain(String nodeId) {
        Step step = allSteps.get(nodeId);
        if (step == null) return nodeId;

        if ("end".equals(step.type)) {
            return nodeId; // 结束节点直接输出自身
        }

        List<Edge> outEdges = outEdgesMap.getOrDefault(nodeId, Collections.emptyList());
        if (outEdges.isEmpty()) {
            return nodeId;
        }

        // 分类
        List<Edge> normalEdges = outEdges.stream()
                .filter(e -> e.maxIterations == null)
                .collect(Collectors.toList());
        List<Edge> loopEdges = outEdges.stream()
                .filter(e -> e.maxIterations != null)
                .collect(Collectors.toList());

        // 单个普通边串联
        if (outEdges.size() == 1 && normalEdges.size() == 1) {
            Edge e = normalEdges.get(0);
            String next = buildChain(e.target);
            if (next.isEmpty()) return nodeId;
            if (next.startsWith("THEN(")) {
                return "THEN(" + nodeId + ", " + next.substring(5, next.length() - 1) + ")";
            } else {
                return "THEN(" + nodeId + ", " + next + ")";
            }
        }

        // 单条循环边
        if (outEdges.size() == 1 && loopEdges.size() == 1) {
            Edge e = loopEdges.get(0);
            return buildLoop(e, nodeId);
        }

        // 多条边：构建 IF-ELIF-ELSE
        StringBuilder branch = new StringBuilder();
        // IF/ELIF 普通边
        if (!normalEdges.isEmpty()) {
            Edge first = normalEdges.get(0);
            String cond = getConditionNode(first);
            String branchContent = buildBranch(first, nodeId);
            branch.append("IF(").append(cond).append(", ").append(branchContent).append(")");
            for (int i = 1; i < normalEdges.size(); i++) {
                Edge e = normalEdges.get(i);
                cond = getConditionNode(e);
                branchContent = buildBranch(e, nodeId);
                branch.append(".ELIF(").append(cond).append(", ").append(branchContent).append(")");
            }
        }
        // ELSE 循环边
        if (!loopEdges.isEmpty()) {
            Edge loopEdge = loopEdges.get(0);
            String branchContent = buildBranch(loopEdge, nodeId); // 会进入 buildLoop
            if (branch.length() == 0) {
                branch.append("IF(true, ").append(branchContent).append(")");
            } else {
                branch.append(".ELSE(").append(branchContent).append(")");
            }
            if (loopEdges.size() > 1) {
                System.err.println("警告：节点 " + nodeId + " 有多条循环边，仅第一条作为 ELSE");
            }
        }

        if (branch.length() == 0) return nodeId;
        return "THEN(" + nodeId + ", " + branch.toString() + ")";
    }

    private String buildBranch(Edge edge, String sourceId) {
        if (edge.maxIterations != null) {
            return buildLoop(edge, sourceId);
        } else {
            String result = buildChain(edge.target);
            return result.isEmpty() ? "" : result;
        }
    }

    private String buildLoop(Edge edge, String sourceId) {
        int maxIter = edge.maxIterations != null ? edge.maxIterations : 1;
        String targetId = edge.target;

        // 查找从 target 到 sourceId 的路径
        List<String> path = findPath(targetId, sourceId);
        if (path.isEmpty()) {
            path = Collections.singletonList(targetId);
        }

        String loopBody = String.join(", ", path);
        String breakCond = "break_" + edge.source + "_" + edge.target;
        String body = "THEN(" + loopBody + ", IF(" + breakCond + ", BREAK, THEN()))";
        return "FOR(" + maxIter + ").DO(" + body + ")";
    }

    private List<String> findPath(String start, String end) {
        List<String> path = new ArrayList<>();
        String current = start;
        Set<String> visited = new HashSet<>();
        while (current != null && !current.equals(end) && !visited.contains(current)) {
            visited.add(current);
            Step step = allSteps.get(current);
            if (step != null && "end".equals(step.type)) break;
            path.add(current);
            List<Edge> edges = outEdgesMap.getOrDefault(current, Collections.emptyList());
            if (edges.isEmpty()) break;
            Edge e = edges.get(0);
            current = e.target;
        }
        if (current != null && current.equals(end)) {
            path.add(end);
            return path;
        }
        return Collections.emptyList();
    }

    private String getConditionNode(Edge edge) {
        if (edge.condition == null || edge.condition.isEmpty()) return "true";
        return "cond_" + edge.source + "_" + edge.target;
    }

    // ---------- 测试 ----------
    public static void main(String[] args) throws Exception {
        JsonNode root = mapper.readTree("{\"steps\":[{\"id\":\"start_1782380476065\",\"type\":\"start\",\"label\":\"开始\",\"order\":1,\"x\":200,\"y\":380},{\"id\":\"process_1782380483404_a6jq\",\"type\":\"process\",\"label\":\"路易斯\",\"order\":2,\"x\":400,\"y\":360,\"agentCode\":\"AGT-0954249de54f4458bc59167ec1889bcb\",\"agentName\":\"路易斯\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/20/20260620174542_70375905acdc47e4854e04bda8075789.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782380489512_qore\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":3,\"x\":660,\"y\":280,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process_1782380493070_smg0\",\"type\":\"process\",\"label\":\"卡特\",\"order\":4,\"x\":660,\"y\":380,\"agentCode\":\"AGT-a25c24fb3d6a4f3290a63d98d8e6cc80\",\"agentName\":\"卡特\",\"agentAvatar\":\"\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"confirm_1782380497440_3eso\",\"type\":\"confirm\",\"label\":\"人工确认\",\"order\":5,\"x\":960,\"y\":320}],\"edges\":[{\"id\":\"edge_start_1782380476065_process_1782380483404_a6jq\",\"source\":\"start_1782380476065\",\"target\":\"process_1782380483404_a6jq\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782380483404_a6jq_process_1782380489512_qore\",\"source\":\"process_1782380483404_a6jq\",\"target\":\"process_1782380489512_qore\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{age}} > 18\",\"label\":\"是\"},{\"id\":\"edge_process_1782380483404_a6jq_process_1782380493070_smg0\",\"source\":\"process_1782380483404_a6jq\",\"target\":\"process_1782380493070_smg0\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{age}} <= 18\",\"label\":\"否\"},{\"id\":\"edge_process_1782380489512_qore_confirm_1782380497440_3eso\",\"source\":\"process_1782380489512_qore\",\"target\":\"confirm_1782380497440_3eso\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge_process_1782380493070_smg0_confirm_1782380497440_3eso\",\"source\":\"process_1782380493070_smg0\",\"target\":\"confirm_1782380497440_3eso\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"}]}");
        LiteFlowELConverter converter = new LiteFlowELConverter();
        ConversionResult result = converter.convertFull(root);

        System.out.println("=== EL 表达式 ===");
        System.out.println(result.elExpression);

        System.out.println("\n=== 节点详情 ===");
        for (NodeDetail detail : result.nodeDetails) {
            System.out.println("ID: " + detail.id);
            System.out.println("  类型: " + detail.type);
            System.out.println("  标签: " + detail.label);
            System.out.println("  顺序: " + detail.order);
            if (detail.agentCode != null) {
                System.out.println("  Agent: " + detail.agentName + " (" + detail.agentCode + ")");
                System.out.println("  模型: " + detail.agentModel);
            }
            if (!detail.skillPaths.isEmpty()) {
                System.out.println("  技能: " + detail.skillPaths);
            }
            if (!detail.outEdges.isEmpty()) {
                System.out.println("  出边:");
                for (NodeDetail.OutEdge oe : detail.outEdges) {
                    System.out.println("    -> " + oe.targetId + " 条件: " + oe.condition + " 循环: " + oe.maxIterations);
                }
            }
            System.out.println();
        }
    }
}