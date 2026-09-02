package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.db.dto.UsageRecord;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 群聊发言者挑选器（群聊通用参与决策组件）。
 * <p>
 * 三层瀑布决策，决定"本次由哪些 Agent 发言、以何顺序"：
 * <ul>
 *   <li><b>L1 被 @ 解析（纯规则）</b>：被 @ 的 Agent 全部必须回应，直接返回该集合，不调 LLM。</li>
 *   <li><b>L2 衔接判断（规则）</b>：无 @，且上一条由某 Agent 回复，则让该 Agent 继续。</li>
 *   <li><b>L3 能力匹配（LLM + 关键词兜底）</b>：基于能力标签匹配，输出 1~N 个 + 顺序，数量由 LLM 决定。</li>
 * </ul>
 * <p>
 * 本方案 chat 分支使用；task 分支（LangGraph 编排）保持现状，未来可复用本组件的"谁先起头"决策。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupResponderPicker {

    private final LlmExecutor llmExecutor;

    /** 命中策略 */
    public static final String STRATEGY_MENTION = "MENTION";
    public static final String STRATEGY_LINK = "LINK";
    public static final String STRATEGY_CAPABILITY = "CAPABILITY";
    public static final String STRATEGY_FALLBACK = "FALLBACK";

    /** L2 衔接判断：作为"继续上一条"依据的最大条数窗口 */
    private static final int LINK_WINDOW = 1;

    /**
     * 决策本次发言者。
     *
     * @param userContent         用户当前消息
     * @param agents              候选 Agent 定义（含能力标签），用于 L3 匹配
     * @param mentionedAgentCodes 被 @ 的 Agent code 集合（已由上层解析，可为空）
     * @param recentMessages      最近群聊消息（含 sender），用于 L2 衔接判断
     * @return 决策结果（含发言顺序列表、命中策略、原因）
     */
    public ResponderPickResult pick(String userContent,
                                    List<AgentDef> agents,
                                    List<String> mentionedAgentCodes,
                                    List<AgentBuildSpec.GroupRecentMessage> recentMessages,int linkWindow) {
        List<AgentDef> candidates = agents == null ? Collections.emptyList() : agents;

        // L1 被 @ 解析
        List<String> mentioned = filterValid(mentionedAgentCodes, candidates);
        if (CollectionUtils.isNotEmpty(mentioned)) {
            return ResponderPickResult.of(mentioned, STRATEGY_MENTION, "被@点名");
        }

        // L2 衔接判断（无 @）
        String linked = linkToPreviousAgent(recentMessages, candidates,linkWindow);
        if (linked != null) {
            return ResponderPickResult.of(Collections.singletonList(linked), STRATEGY_LINK, "承接上一条回复");
        }

        // L3 能力匹配
        return pickByCapability(userContent, candidates);
    }

    // ---------------- L1 / L2 ----------------

    /** 过滤掉不在候选集合内的 code，避免 @ 了不存在的 Agent。 */
    private List<String> filterValid(List<String> codes, List<AgentDef> candidates) {
        if (CollectionUtils.isEmpty(codes)) {
            return Collections.emptyList();
        }
        return codes.stream()
                .filter(StringUtils::isNotBlank)
                .filter(code -> candidates.stream().anyMatch(a -> a.getCode().equals(code)))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * L2 衔接判断：取最近一条由 Agent 发言的消息，若其 sender 是候选之一，则让该 Agent 继续。
     * 简单规则实现（不做深度语义连贯判断），窗口内第一条 AGENT 发言即视为衔接目标。
     */
    private String linkToPreviousAgent(List<AgentBuildSpec.GroupRecentMessage> recentMessages,
                                       List<AgentDef> candidates,int linkWindow) {
        if (CollectionUtils.isEmpty(recentMessages)) {
            return null;
        }
        int limit = Math.min(recentMessages.size(), linkWindow);
        for (int i = 0; i < limit; i++) {
            AgentBuildSpec.GroupRecentMessage msg = recentMessages.get(i);
            if (msg == null) {
                continue;
            }
            // 上一条是 Agent 发言，且该 Agent 在候选内 → 衔接
            if (isAgentMessage(msg) && candidates.stream().anyMatch(a -> a.getCode().equals(msg.getSenderCode()))) {
                return msg.getSenderCode();
            }
        }
        return null;
    }

    private boolean isAgentMessage(AgentBuildSpec.GroupRecentMessage msg) {
        // 按 senderType 判断，缺省时按 AGENT 处理（群聊里非用户发言多为 Agent）
        if (StringUtils.isNotBlank(msg.getSenderType())) {
            return "AGENT".equalsIgnoreCase(msg.getSenderType());
        }
        return true;
    }

    // ---------------- L3 ----------------

    /**
     * L3 能力匹配：先做「用户消息 ∩ 能力标签」关键词 overlap，命中唯一则直接返回；
     * 否则用 LLM 基于能力标签清单挑选 1~N 个 + 顺序。
     */
    private ResponderPickResult pickByCapability(String userContent, List<AgentDef> candidates) {
        // 关键词 overlap 兜底：命中唯一 Agent
        List<String> matched = matchByKeyword(userContent, candidates);
        if (matched.size() == 1) {
            log.info("agent matched by keyword. userContent={}, agent={}", userContent, matched.get(0));
            return ResponderPickResult.of(matched, STRATEGY_CAPABILITY, "能力标签关键词命中");
        }

        // LLM 能力匹配
        try {
            return pickByLlm(userContent, candidates);
        } catch (Exception e) {
            log.warn("pick by llm error, fallback to first candidate. msg={}", userContent, e);
            // 兜底：无匹配时返回第一个候选（或空）
            return fallback(candidates);
        }
    }

    /**
     * 用户消息分词与能力标签的 overlap 匹配。命中唯一 Agent 时返回该 code，否则返回空/多个。
     */
    private List<String> matchByKeyword(String userContent, List<AgentDef> candidates) {
        if (StringUtils.isBlank(userContent)) {
            return Collections.emptyList();
        }
        List<String> hits = new ArrayList<>();
        for (AgentDef agent : candidates) {
            List<String> caps = agent.getCapabilities();
            if (CollectionUtils.isEmpty(caps)) {
                continue;
            }
            boolean hit = caps.stream().anyMatch(cap ->
                    StringUtils.isNotBlank(cap) && userContent.contains(cap));
            if (hit) {
                hits.add(agent.getCode());
            }
        }
        return hits;
    }

    /**
     * LLM 能力匹配：基于 Agent 能力标签清单挑选发言者。
     */
    private ResponderPickResult pickByLlm(String userContent, List<AgentDef> candidates) {
        String agentList = candidates.stream()
                .map(a -> a.getCode() + "(" + StringUtils.defaultString(a.getName()) + "):" +
                        String.join(",", a.getCapabilities() == null ? Collections.emptyList() : a.getCapabilities()))
                .collect(Collectors.joining("; "));

        String prompt = """
                你是群聊发言者挑选器。根据用户消息和候选 Agent 的能力标签，判断应由哪些 Agent 发言。
                规则：
                - 优先选择能力标签与用户消息最匹配的 Agent
                - 默认只选 1 个 Agent，除非用户明显需要多角色协作
                - 若没有 Agent 能力匹配，选择最可能回应闲聊的 1 个
                - 输出顺序即发言顺序

                候选 Agent（code(名称):能力标签）：
                %s

                用户消息：
                %s

                请仅输出 JSON：{"agents": ["code1", "code2"], "reason": "简短原因"}
                """.formatted(agentList, StringUtils.defaultString(userContent));

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build();
        UsageRecord usage = UsageRecord.builder()
                .agentCode("GROUP_RESPONDER_PICKER")
                .usageType("CHAT")
                .build();
        String content = llmExecutor.chat(request, usage);
        if (StringUtils.isBlank(content)) {
            return fallback(candidates);
        }

        String cleaned = content.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        PickJson json = JsonUtils.toObj(cleaned, PickJson.class);
        if (json == null || CollectionUtils.isEmpty(json.getAgents())) {
            return fallback(candidates);
        }

        // 过滤：只保留候选集合内的 code，并去重保序
        List<String> valid = json.getAgents().stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .filter(code -> candidates.stream().anyMatch(a -> a.getCode().equals(code)))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(valid)) {
            return fallback(candidates);
        }
        return ResponderPickResult.of(valid, STRATEGY_CAPABILITY, json.getReason());
    }

    /** 兜底：返回第一个候选 Agent（若存在）。 */
    private ResponderPickResult fallback(List<AgentDef> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return ResponderPickResult.of(Collections.emptyList(), STRATEGY_FALLBACK, "无候选");
        }
        return ResponderPickResult.of(Collections.singletonList(candidates.get(0).getCode()),
                STRATEGY_FALLBACK, "兜底选择");
    }

    /** 决策结果 DTO */
    @Data
    public static class ResponderPickResult {
        /** 发言顺序列表（去重、保序） */
        private List<String> agentCodes;
        /** 命中策略：MENTION / LINK / CAPABILITY / FALLBACK */
        private String strategy;
        /** 决策原因 */
        private String reason;

        public static ResponderPickResult of(List<String> agentCodes, String strategy, String reason) {
            ResponderPickResult r = new ResponderPickResult();
            r.setAgentCodes(agentCodes);
            r.setStrategy(strategy);
            r.setReason(reason);
            return r;
        }
    }

    /** LLM 挑选结果 DTO */
    @Data
    public static class PickJson {
        private List<String> agents;
        private String reason;
    }
}
