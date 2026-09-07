package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.db.dto.UsageRecord;
import com.xiaomizhou.dpsk.db.dto.WorkflowTemplateDto;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.workflow.xyflow.NodeStep;
import com.xiaomizhou.dpsk.workflow.xyflow.XyFlow;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专家团入口意图识别：判断用户输入是「非任务闲聊」「与本专家团（模板）不相关的任务」「可执行的任务」。
 *
 * <p>策略（保守放行，拦不住也没关系，依旧进入正常执行流程）：
 * <ul>
 *   <li>规则快速命中（问候/寒暄/确认短句）→ NOT_TASK，提示"这里是任务模式，请输入具体任务"；</li>
 *   <li>否则用轻量 LLM 结合模板名/描述/节点 Agent 能力，判断是否任务 & 是否与图相关；</li>
 *   <li>异常或拿不准 → 一律 PASS（放行，避免误拦正常任务）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpertIntentClassifier {

    /** 判定结果 */
    public enum Verdict {
        /** 非任务（闲聊/寒暄/确认），提示用户输入具体任务 */
        NOT_TASK,
        /** 是任务但与本专家团能力不相关，提示换一个匹配的专家团 */
        UNRELATED,
        /** 通过，正常进入工作流执行 */
        PASS
    }

    private final LlmExecutor llmExecutor;
    private final AgentDefProvider agentDefProvider;

    /**
     * 评估一条专家团输入。
     *
     * @param userContent 用户输入
     * @param template    当前专家团模板（含 name/description/workflowJson）
     * @return Verdict，保守放行
     */
    public Verdict evaluate(String userContent, WorkflowTemplateDto template) {
        if (StringUtils.isBlank(userContent)) {
            return Verdict.PASS;
        }
        String trimmed = userContent.trim();

        // Step 1: 规则快速命中（纯闲聊/寒暄 → NOT_TASK，不调 LLM）
        if (isChatByRule(trimmed)) {
            return Verdict.NOT_TASK;
        }

        // Step 2: LLM 判断任务意图 + 与模板相关性；异常/拿不准 → PASS
        try {
            return evaluateByLlm(trimmed, template);
        } catch (Exception e) {
            log.warn("expert intent classify by llm error, default PASS. msg={}, err={}", trimmed, e.getMessage());
            return Verdict.PASS;
        }
    }

    /**
     * 规则快速命中：问候语、寒暄、简短的情绪表达、确认性短句等 → 非任务。
     */
    private boolean isChatByRule(String text) {
        if (text.length() > 30) {
            // 长消息大概率是任务，跳过规则
            return false;
        }
        for (String keyword : new String[]{
                "你好", "您好", "嗨", "哈喽", "hello", "hi", "早上好", "中午好", "晚上好",
                "在吗", "有人吗", "谢谢", "感谢", "再见", "拜拜", "晚安", "哈哈", "嘿嘿",
                "辛苦", "好的", "收到", "嗯嗯", "是的", "对", "👍", "👌", "😊", "😄",
                "嗯", "哦", "？", "?"
        }) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * LLM 评估：输出 {intent: "TASK"|"CHAT", related: true|false}。
     */
    private Verdict evaluateByLlm(String text, WorkflowTemplateDto template) {
        String templateDesc = describeTemplate(template);

        String prompt = """
                你是专家团入口质检员。判断用户输入是否符合当前专家团的定位：
                1) 是否为「具体任务」（需要动手做事：生成代码/写文档/分析/调研/执行多步骤工作），还是「闲聊/寒暄/随口感叹」；
                2) 若是任务，判断该任务是否与当前专家团的能力/定位相关。

                专家团信息：
                %s

                用户输入：
                %s

                判定规则：
                - 纯闲聊、寒暄、问候、情绪表达、感谢、简单确认 → intent=CHAT
                - 需要动手做事的表述 → intent=TASK
                - intent=TASK 时：任务与专家团能力相关 → related=true；明显不相关（让不擅长该领域的专家团去做的任务）→ related=false
                - 拿不准的，倾向 intent=TASK 且 related=true（宁可放行，也不要误拦）

                请仅输出 JSON：{"intent": "CHAT"}、{"intent": "TASK", "related": true} 或 {"intent": "TASK", "related": false}
                """.formatted(templateDesc, text);

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build();
        UsageRecord usage = UsageRecord.builder()
                .agentCode("EXPERT_INTENT_CLASSIFIER")
                .usageType("OTHER")
                .build();
        String content = llmExecutor.chat(request, usage);
        if (StringUtils.isBlank(content)) {
            return Verdict.PASS;
        }

        // 提取 JSON
        String cleaned = content.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        VerdictJson json = null;
        try {
            json = JsonUtils.toObj(cleaned, VerdictJson.class);
        } catch (Exception ignore) {
            // 解析失败走兜底
        }
        if (json == null || json.getIntent() == null) {
            // 兜底：无法解析 → 放行
            return Verdict.PASS;
        }
        boolean isTask = json.getIntent().toUpperCase().contains("TASK");
        if (!isTask) {
            return Verdict.NOT_TASK;
        }
        // 是任务：related 非 false 一律放行（拿不准当相关）
        return Boolean.FALSE.equals(json.getRelated()) ? Verdict.UNRELATED : Verdict.PASS;
    }

    /** 将模板名/描述/节点 Agent 能力拼成上下文文本，供 LLM 判断相关性。 */
    private String describeTemplate(WorkflowTemplateDto template) {
        StringBuilder sb = new StringBuilder();
        if (template != null) {
            if (StringUtils.isNotBlank(template.getName())) {
                sb.append("专家团名称：").append(template.getName()).append("\n");
            }
            if (StringUtils.isNotBlank(template.getDescription())) {
                sb.append("专家团描述：").append(template.getDescription()).append("\n");
            }
            String agents = describeAgents(template.getWorkflowJson());
            if (StringUtils.isNotBlank(agents)) {
                sb.append("专家团成员及能力：\n").append(agents);
            }
        }
        if (sb.isEmpty()) {
            sb.append("（无模板信息）");
        }
        return sb.toString().trim();
    }

    /** 从 workflowJson 提取节点 agent code，并拼接各 Agent 能力描述。 */
    private String describeAgents(String workflowJson) {
        if (StringUtils.isBlank(workflowJson)) {
            return "";
        }
        try {
            XyFlow xyFlow = JsonUtils.toObj(workflowJson, XyFlow.class);
            if (xyFlow == null || xyFlow.getSteps() == null) {
                return "";
            }
            List<String> agentCodes = new ArrayList<>();
            for (NodeStep step : xyFlow.getSteps()) {
                // 普通 agent 节点：agentCode 非空（type=process 或内置类型均带 agentCode 才会被收集）
                if (StringUtils.isNotBlank(step.getAgentCode())) {
                    agentCodes.add(step.getAgentCode());
                }
            }
            if (agentCodes.isEmpty()) {
                return "";
            }
            Map<String, AgentDef> defMap = new LinkedHashMap<>();
            List<AgentDef> defs = agentDefProvider.getByCodes(agentCodes);
            if (defs != null) {
                for (AgentDef def : defs) {
                    if (def != null && def.getCode() != null) {
                        defMap.put(def.getCode(), def);
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (String code : agentCodes) {
                AgentDef def = defMap.get(code);
                if (def != null && def.getPrompt() != null) {
                    sb.append("- [").append(code).append("] ")
                            .append(def.toPersonaText().replace("\n", " ").trim()).append("\n");
                } else {
                    sb.append("- [").append(code).append("] （未知 Agent）\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("describe agents failed, workflowJson parse error", e);
            return "";
        }
    }

    /** 判定结果 DTO */
    @Data
    public static class VerdictJson {
        private String intent;
        private Boolean related;
    }
}
