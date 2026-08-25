package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeEdge;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 规划器：调用 LLM 生成满足需求的工作流 XyFlow 图（Phase 1 采用 JSON 约束解析，不绑定 json_schema）。
 *
 * <p>输出图随后交由 {@link PlanValidator} 做结构性校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowPlanner {

    private final AgentComponentFactory agentComponentFactory;
    private final AgentDefProvider agentDefProvider;

    /**
     * 生成规划图（JsonSchema 结构化输出）。
     *
     * @param request 规划请求（含用户诉求、候选 Agent、replan 历史）
     * @return 生成的 XyFlow 图
     * @throws RuntimeException 解析失败时抛出（由上层捕获并标记失败）
     */
    public XyFlow plan(PlanningRequest request) {
        String prompt = PlannerPrompt.build(
                buildAgentPool(request.getAgentCodes()),
                request.getHistory(),
                request.getUserRequest(),
                request.getHistory() != null && !request.getHistory().isEmpty());

        ChatModel model = agentComponentFactory.createChatModel();

        // 结构化输出：以 JsonSchema 约束 planner 返回严格 JSON，避免自由文本解析失败
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
//                .responseFormat(buildJsonResponseFormat())
                .build());

        String content = response.aiMessage().text();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("planner 模型未返回内容");
        }

        String cleaned = stripMarkdownFence(content);
        PlanJson plan = JsonUtils.toObj(cleaned, PlanJson.class);
        if (plan == null || plan.getNodes() == null || plan.getNodes().isEmpty()) {
            throw new IllegalStateException("planner 返回的 JSON 无法解析为工作流");
        }

        return toXyFlow(plan);
    }

    /**
     * 将候选 Agent code 列表解析为"候选 Agent 池"文本列表。
     *
     * <p>每条文本格式：code 括号开头 + {@link AgentDef#toPersonaText()} 的能力/人设描述，
     * 让 LLM 能基于每个 Agent 的实际能力决定如何编排（而不只是看到一个 code）。
     *
     * @param agentCodes 候选 Agent code 列表
     * @return 候选池文本列表；解析不到的 code 也以「code（未知 Agent）」兜底保留
     */
    private List<String> buildAgentPool(List<String> agentCodes) {
        if (agentCodes == null || agentCodes.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.Map<String, AgentDef> defMap = new java.util.LinkedHashMap<>();
        List<AgentDef> defs = agentDefProvider.getByCodes(agentCodes);
        if (defs != null) {
            for (AgentDef def : defs) {
                if (def != null && def.getCode() != null) {
                    defMap.put(def.getCode(), def);
                }
            }
        }
        List<String> pool = new ArrayList<>();
        for (String code : agentCodes) {
            AgentDef def = defMap.get(code);
            if (def != null && def.getPrompt() != null) {
                pool.add("[" + code + "] " + def.toPersonaText().replace("\n", " ").trim());
            } else {
                pool.add("[" + code + "] （未知 Agent，无能力描述）");
            }
        }
        return pool;
    }

    /**
     * 构造 planner 输出的 JsonSchema：
     * <pre>
     * {
     *   "nodes": [{ "id", "type", "label", "task" }],
     *   "edges": [{ "source", "target", "condition", "label" }]
     * }
     * </pre>
     */
    private ResponseFormat buildJsonResponseFormat() {
        JsonObjectSchema nodeSchema = JsonObjectSchema.builder()
                .addStringProperty("id", "节点唯一 id，如 n1/n2/n3")
                .addStringProperty("type", "节点类型：start/end/switch/候选 Agent code")
                .addStringProperty("label", "节点显示名")
                .addStringProperty("task", "节点任务指令（agent/switch 节点必填）")
                .required(java.util.List.of("id", "type"))
                .build();

        JsonObjectSchema edgeSchema = JsonObjectSchema.builder()
                .addStringProperty("source", "出边节点 id")
                .addStringProperty("target", "入边节点 id")
                .addStringProperty("condition", "边条件（switch 出边必填）")
                .addStringProperty("label", "边显示名")
                .required(java.util.List.of("source", "target"))
                .build();

        JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty("nodes", JsonArraySchema.builder().items(nodeSchema).build())
                .addProperty("edges", JsonArraySchema.builder().items(edgeSchema).build())
                .required(java.util.List.of("nodes", "edges"))
                .build();

        JsonSchema schema = JsonSchema.builder()
                .name("WorkflowPlan")
                .rootElement(root)
                .build();

        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(schema)
                .build();
    }

    /** 把 LLM 返回的 PlanJson 翻译为 XyFlow（填充 NodeStep 与 NodeEdge）。 */
    private XyFlow toXyFlow(PlanJson plan) {
        XyFlow xyFlow = new XyFlow();

        List<NodeStep> steps = new ArrayList<>();
        for (PlanJson.PlanNode n : plan.getNodes()) {
            NodeStep step = new NodeStep();
            step.setId(n.getId());

            // type 归一化：LLM 输出的 type 可能是内置类型名（start/end/switch/...）或 agent code。
            //  - 内置类型名 → 直接作为节点类型（nodeType），agentCode 为空；
            //  - 否则视为 agent code → 节点类型固定为 process（普通 agent），agentCode 存具体 code。
            //  这样 NodeExecutorRegistry 才能按 "process" 找到 AgentNodeExecutor，并经 agentCode 区分具体 Agent。
            Integer builtin = NodeStep.getNodeTypeByName(n.getType());
            if (builtin != null) {
                step.setType(n.getType());
                step.setAgentCode(null);
            } else {
                step.setType(NodeStep.NODE_TYPE_COMMON_AGENT.left);
                step.setAgentCode(n.getType());
            }
            step.setLabel(n.getLabel() != null ? n.getLabel() : n.getType());
            // 节点任务指令由 systemPrompt 承载，XyFlowContextBuilder 会将其注入 NodeContext.prompt，
            // 供 Agent 节点执行器拼装任务上下文。
            step.setSystemPrompt(n.getTask());
            steps.add(step);
        }
        xyFlow.setSteps(steps);

        List<NodeEdge> edges = new ArrayList<>();
        if (plan.getEdges() != null) {
            int idx = 0;
            for (PlanJson.PlanEdge e : plan.getEdges()) {
                NodeEdge edge = new NodeEdge();
                edge.setId("e" + (++idx));
                edge.setSource(e.getSource());
                edge.setTarget(e.getTarget());
                edge.setCondition(e.getCondition());
                edge.setLabel(e.getLabel() != null ? e.getLabel() : e.getCondition());
                edges.add(edge);
            }
        }
        xyFlow.setEdges(edges);
        return xyFlow;
    }

    /** 去除 LLM 返回中可能的 markdown 代码块包裹。 */
    private String stripMarkdownFence(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }

    /** planner 输出的 JSON DTO。 */
    @Data
    public static class PlanJson {
        private List<PlanNode> nodes;
        private List<PlanEdge> edges;

        @Data
        public static class PlanNode {
            private String id;
            private String type;
            private String label;
            private String task;
        }

        @Data
        public static class PlanEdge {
            private String source;
            private String target;
            private String condition;
            private String label;
        }
    }
}
