package com.xiaomizhou.dpsk.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 将包含 steps 和 edges 的 DAG JSON 转换为 LiteFlow EL 表达式。
 * <p>
 * 转换规则：
 * <ul>
 *   <li>按 order 排序节点，但最终执行顺序由边决定</li>
 *   <li>单个出边：直接串联 (THEN)</li>
 *   <li>多个出边：按给定顺序构建 IF-ELIF-ELSE</li>
 *   <li>带 maxIterations 的边：视为循环，生成 FOR 循环，并在循环体内加入条件判断 BREAK</li>
 * </ul>
 * 需要用户自行实现所有条件节点（以 "cond_" 为前缀）和循环退出条件节点（以 "break_" 为前缀）。
 */
public class LiteFlowELConverterBak {

    private static final ObjectMapper mapper = new ObjectMapper();

    // 节点实体
    static class Step {
        String id;
        String type;
        String label;
        int order;
        // 其他字段忽略
    }

    // 边实体
    static class Edge {
        String source;
        String target;
        String condition;      // 可为空
        String label;
        Integer maxIterations; // 可为空
    }

    private final Map<String, Step> stepMap = new HashMap<>();
    private final Map<String, List<Edge>> outEdgesMap = new HashMap<>();
    private final Map<String, Step> allSteps = new HashMap<>();

    public String convert(JsonNode root) {
        // 1. 解析节点
        ArrayNode stepsNode = (ArrayNode) root.get("steps");
        for (JsonNode s : stepsNode) {
            Step step = new Step();
            step.id = s.get("id").asText();
            step.type = s.get("type").asText();
            step.label = s.get("label").asText();
            step.order = s.get("order").asInt();
            allSteps.put(step.id, step);
        }

        // 2. 解析边
        ArrayNode edgesNode = (ArrayNode) root.get("edges");
        for (JsonNode e : edgesNode) {
            Edge edge = new Edge();
            edge.source = e.get("source").asText();
            edge.target = e.get("target").asText();
            edge.condition = e.has("condition") ? e.get("condition").asText() : null;
            edge.label = e.has("label") ? e.get("label").asText() : "";
            if (e.has("maxIterations")) {
                edge.maxIterations = e.get("maxIterations").asInt();
            }
            outEdgesMap.computeIfAbsent(edge.source, k -> new ArrayList<>()).add(edge);
        }

        // 3. 找到起始节点 (type=start)
        String startId = allSteps.values().stream()
                .filter(s -> "start".equals(s.type))
                .map(s -> s.id)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到 start 节点"));

        // 4. 构建 EL
        String chainBody = buildChain(startId);
        return "<chain name=\"chain1\">\n    " + chainBody + "\n</chain>";
    }

    /**
     * 递归构建从某个节点开始的执行序列
     */
    private String buildChain(String nodeId) {
        Step step = allSteps.get(nodeId);
        if (step == null) return "node(\"" + nodeId + "\")";

        // 终止节点
        if ("end".equals(step.type)) {
            return "node(\"" + nodeId + "\")";
        }

        List<Edge> outEdges = outEdgesMap.getOrDefault(nodeId, Collections.emptyList());
        if (outEdges.isEmpty()) {
            return "node(\"" + nodeId + "\")";
        }

        // 单个出边：串联
        if (outEdges.size() == 1) {
            Edge e = outEdges.get(0);
            String next = buildChain(e.target);
            if (e.maxIterations != null) {
                // 单边循环（罕见），生成循环结构
                return buildLoop(e, nodeId);
            } else {
                return "THEN(node(\"" + nodeId + "\"), " + next + ")";
            }
        }

        // 多个出边：构建 IF-ELIF-ELSE
        // 按给定顺序处理
        List<Edge> edges = outEdges;
        // 分离出循环边（有maxIterations）和普通边
        List<Edge> normalEdges = edges.stream()
                .filter(e -> e.maxIterations == null)
                .collect(Collectors.toList());
        List<Edge> loopEdges = edges.stream()
                .filter(e -> e.maxIterations != null)
                .collect(Collectors.toList());

        // 简化：将循环边作为最后一条（ELSE），普通边按顺序作为 IF/ELIF
        // 如果有多个循环边，只取第一个作为 ELSE，其余忽略（警告）
        if (loopEdges.size() > 1) {
            System.err.println("警告：节点 " + nodeId + " 有多条循环边，仅第一条作为 ELSE");
        }

        StringBuilder el = new StringBuilder();

        // 先处理普通边，构建 IF/ELIF
        if (!normalEdges.isEmpty()) {
            // 第一个普通边作为 IF
            Edge first = normalEdges.get(0);
            String condNode = getConditionNode(first);
            String branchContent = buildBranch(first, nodeId);
            el.append("IF(").append(condNode).append(", ").append(branchContent).append(")");

            // 后续普通边作为 ELIF
            for (int i = 1; i < normalEdges.size(); i++) {
                Edge e = normalEdges.get(i);
                condNode = getConditionNode(e);
                branchContent = buildBranch(e, nodeId);
                el.append(".ELIF(").append(condNode).append(", ").append(branchContent).append(")");
            }
        }

        // 处理循环边（作为 ELSE）
        if (!loopEdges.isEmpty()) {
            Edge loopEdge = loopEdges.get(0);
            String branchContent = buildBranch(loopEdge, nodeId);
            if (el.length() == 0) {
                // 没有普通边，直接使用 IF? 但这里作为 ELSE 可能不合理，但允许
                el.append("IF(true, ").append(branchContent).append(")");
            } else {
                el.append(".ELSE(").append(branchContent).append(")");
            }
        }

        // 如果没有循环边且普通边数量>0，但缺少 ELSE？此处不处理，留给用户检查
        // 但为了完整性，如果所有边都有条件，最后一个条件可能作为 ELSE ？但逻辑上应该至少有一个无条件或默认分支。
        // 这里简单处理：如果没有循环边且所有边都有条件，我们将最后一条作为 ELSE（不推荐，但示例中所有边都有条件，且循环边作为 ELSE，已处理）

        // 返回该节点的执行表达式，前面加上该节点自身（但分支已经包含了从本节点出发的后续，本节点的执行应该在分支之前）
        // 所以如果节点有多个出边，本节点也需要执行，我们应在分支前执行本节点。
        return "THEN(node(\"" + nodeId + "\"), " + el.toString() + ")";
    }

    /**
     * 构建一个分支的执行体（可能是普通节点序列或循环）
     */
    private String buildBranch(Edge edge, String sourceId) {
        if (edge.maxIterations != null) {
            // 循环分支
            return buildLoop(edge, sourceId);
        } else {
            // 普通分支：从 target 继续
            return buildChain(edge.target);
        }
    }

    /**
     * 构建循环表达式
     * @param edge 带有 maxIterations 的边
     * @param sourceId 边的源节点（循环体执行完后会重新执行该节点）
     * @return EL 字符串
     */
    private String buildLoop(Edge edge, String sourceId) {
        String targetId = edge.target;
        Integer maxIter = edge.maxIterations;

        // 1. 找到从 targetId 到 sourceId 的路径（简单情况：直接边）
        // 这里使用简单方法：从 target 开始，通过唯一的出边追踪，直到回到 source。
        // 如果有多条路径，只取第一条（简化）
        List<String> pathNodes = findPath(targetId, sourceId);
        if (pathNodes.isEmpty()) {
            // 如果没有找到路径，则循环体只包含 target
            pathNodes = Collections.singletonList(targetId);
        }

        // 构建循环体：THEN(节点序列)
        String loopBody = pathNodes.stream()
                .collect(Collectors.joining(", ", "THEN(", ")"));

        // 循环条件：如果循环条件不成立，则 BREAK
        // 将 edge.condition 取反作为 BREAK 条件（假设 condition 是 EL 表达式）
        // 因为 EL 中不能直接写表达式，需要条件节点，我们生成一个 break 条件节点名
        String breakCondNode = "break_" + edge.source + "_" + edge.target;
        // 循环体内部加上 IF(breakCondNode, THEN(BREAK), ELSE(THEN()))
        String loopBodyWithBreak = "THEN(" +
                loopBody.substring(5, loopBody.length() - 1) + // 去掉 THEN( 和 )
                ", IF(" + breakCondNode + ", THEN(BREAK), ELSE(THEN()))" +
                ")";

        // 如果 maxIterations 为 null 或 <=0，默认 1
        int iter = (maxIter != null && maxIter > 0) ? maxIter : 1;
        return "FOR(" + iter + ").DO(" + loopBodyWithBreak + ")";
    }

    /**
     * 简单查找从 start 到 end 的路径（按边追踪，假设唯一）
     */
    private List<String> findPath(String start, String end) {
        List<String> path = new ArrayList<>();
        String current = start;
        Set<String> visited = new HashSet<>();
        while (current != null && !current.equals(end) && !visited.contains(current)) {
            visited.add(current);
            path.add(current);
            List<Edge> edges = outEdgesMap.getOrDefault(current, Collections.emptyList());
            if (edges.isEmpty()) {
                break;
            }
            // 取第一条边
            Edge e = edges.get(0);
            current = e.target;
        }
        if (current != null && current.equals(end)) {
            path.add(end); // 包含 end
        } else {
            // 未找到，返回空
            return Collections.emptyList();
        }
        return path;
    }

    /**
     * 获取条件节点名称（用于 IF/ELIF）
     */
    private String getConditionNode(Edge edge) {
        if (edge.condition == null || edge.condition.isEmpty()) {
            return "true"; // 无条件，但一般不会在 IF 中使用
        }
        // 生成唯一条件节点名
        return "cond_" + edge.source + "_" + edge.target;
    }

    public static void main(String[] args) throws Exception {
        // 读取 JSON（此处从文件读取，请根据实际情况修改）
        JsonNode root = mapper.readTree("{\"steps\":[{\"id\":\"start-1782370169868\",\"type\":\"start\",\"label\":\"开始\",\"order\":1,\"x\":80,\"y\":440},{\"id\":\"process-1782370179116-brzn\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":2,\"x\":300,\"y\":400,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[\"humanizer-1-0-0\"]},{\"id\":\"process-1782370198383-0gkp\",\"type\":\"process\",\"label\":\"卡特\",\"order\":3,\"x\":440,\"y\":240,\"agentCode\":\"AGT-a25c24fb3d6a4f3290a63d98d8e6cc80\",\"agentName\":\"卡特\",\"agentAvatar\":\"\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"end-1782370224207-axpt\",\"type\":\"end\",\"label\":\"结束\",\"order\":4,\"x\":1540,\"y\":480},{\"id\":\"confirm-1782370610688-zk8g\",\"type\":\"confirm\",\"label\":\"人工确认\",\"order\":5,\"x\":920,\"y\":500,\"mcpCodes\":[],\"skillPaths\":[]},{\"id\":\"process-1782370761255-jd6g\",\"type\":\"process\",\"label\":\"UI设计师\",\"order\":6,\"x\":720,\"y\":140,\"agentCode\":\"AGT-84e0c01efeb143008aa569746ced2203\",\"agentName\":\"UI设计师\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005555_133c7342ade243afb97f2c32d12e4258.jpg\",\"agentModel\":\"agnes-2.0-flash\",\"mcpCodes\":[],\"skillPaths\":[\"weather-1-0-0\"]},{\"id\":\"process-1782370781351-shuc\",\"type\":\"process\",\"label\":\"嘴炮辩论者\",\"order\":7,\"x\":1000,\"y\":-80,\"agentCode\":\"AGT-ed91220de76745dba42e2e190dca1a2a\",\"agentName\":\"嘴炮辩论者\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612125826_7d137902203843918f4a0b4b68de18ce.jpg\",\"agentModel\":\"qwen-max\",\"mcpCodes\":[],\"skillPaths\":[],\"description\":\"开始开发\"},{\"id\":\"process-1782370815921-vv6p\",\"type\":\"process\",\"label\":\"刘小花\",\"order\":8,\"x\":1260,\"y\":140,\"agentCode\":\"AGT-27546edc533441e4ab65c1a119388c4a\",\"agentName\":\"刘小花\",\"agentAvatar\":\"http://127.0.0.1:8080/uploads/2026/06/12/20260612005056_a98d9ac2be2f4bd2a5ec7b875dffd6a7.jpg\",\"agentModel\":\"deepseek-v4-flash\",\"mcpCodes\":[],\"skillPaths\":[],\"description\":\"开始测试\"}],\"edges\":[{\"id\":\"edge-start-1782370169868-process-1782370179116-brzn\",\"source\":\"start-1782370169868\",\"target\":\"process-1782370179116-brzn\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge-process-1782370179116-brzn-process-1782370198383-0gkp\",\"source\":\"process-1782370179116-brzn\",\"target\":\"process-1782370198383-0gkp\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge-process-1782370198383-0gkp-confirm-1782370610688-zk8g\",\"source\":\"process-1782370198383-0gkp\",\"target\":\"confirm-1782370610688-zk8g\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{score}} > 70\",\"label\":\"达标\"},{\"id\":\"xy-edge__process-1782370198383-0gkpsource-process-1782370179116-brzntarget\",\"source\":\"process-1782370198383-0gkp\",\"target\":\"process-1782370179116-brzn\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{score}} < 50\",\"label\":\"否\",\"maxIterations\":10},{\"id\":\"edge-process-1782370198383-0gkp-process-1782370761255-jd6g\",\"source\":\"process-1782370198383-0gkp\",\"target\":\"process-1782370761255-jd6g\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"{{score|| > 90\",\"label\":\"{{score|| > 90\"},{\"id\":\"edge-process-1782370761255-jd6g-process-1782370781351-shuc\",\"source\":\"process-1782370761255-jd6g\",\"target\":\"process-1782370781351-shuc\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"edge-process-1782370781351-shuc-process-1782370815921-vv6p\",\"source\":\"process-1782370781351-shuc\",\"target\":\"process-1782370815921-vv6p\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"xy-edge__process-1782370815921-vv6psource-end-1782370224207-axpttarget\",\"source\":\"process-1782370815921-vv6p\",\"target\":\"end-1782370224207-axpt\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"},{\"id\":\"xy-edge__confirm-1782370610688-zk8gsource-end-1782370224207-axpttarget\",\"source\":\"confirm-1782370610688-zk8g\",\"target\":\"end-1782370224207-axpt\",\"sourceHandle\":\"source\",\"targetHandle\":\"target\",\"condition\":\"\",\"label\":\"\"}]}");

        LiteFlowELConverterBak converter = new LiteFlowELConverterBak();
        String el = converter.convert(root);
        System.out.println(el);
    }

}
