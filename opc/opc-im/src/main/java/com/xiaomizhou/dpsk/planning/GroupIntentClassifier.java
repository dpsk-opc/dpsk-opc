package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 群聊意图分类器：区分「闲聊（CHAT）」与「复杂任务（TASK）」。
 *
 * <p>策略：规则快速命中（问候/寒暄/简单情绪）→ CHAT，避免每轮都调 LLM；
 * 未命中规则则用轻量 LLM 分类。与 README §9.1.2「规则 + 轻量 LLM」两段式设计一致。
 *
 * <p>分类结果：
 * <ul>
 *   <li>CHAT — 闲聊/寒暄/简单问答，交由群聊 Supervisor（suggestedAgent）直接对话，不建任务、不做编排；</li>
 *   <li>TASK — 需要多 Agent 协作的复杂任务，进入 {@link WorkflowPlanner} 规划编排 + LangGraph 执行。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupIntentClassifier {

    private final AgentComponentFactory agentComponentFactory;

    /** 消息类型 */
    public static final String INTENT_CHAT = "CHAT";
    public static final String INTENT_TASK = "TASK";

    /**
     * 分类群聊消息意图。
     *
     * @param userContent 用户消息
     * @return CHAT / TASK
     */
    public String classify(String userContent) {
        if (userContent == null || userContent.isBlank()) {
            return INTENT_CHAT;
        }
        String trimmed = userContent.trim();

        // Step 1: 规则快速命中（纯闲聊/寒暄 → CHAT，不调 LLM）
        if (isChatByRule(trimmed)) {
            return INTENT_CHAT;
        }

        // Step 2: 轻量 LLM 兜底分类
        try {
            return classifyByLlm(trimmed);
        } catch (Exception e) {
            // 分类异常时保守走 CHAT（宁可多聊天，也不要对闲聊做重编排）
            log.warn("group intent classify by llm error, default CHAT. msg={}, err={}", trimmed, e.getMessage());
            return INTENT_CHAT;
        }
    }

    /**
     * 规则快速命中：问候语、寒暄、简短的情绪表达、感谢、告别等 → CHAT。
     */
    private boolean isChatByRule(String text) {
        if (text.length() > 30) {
            // 长消息大概率是任务，跳过规则
            return false;
        }
        for (String keyword : new String[]{
                "你好", "您好", "嗨", "哈喽", "hello", "hi", "早上好", "中午好", "晚上好",
                "在吗", "有人吗", "谢谢", "感谢", "再见", "拜拜", "晚安", "哈哈", "嘿嘿",
                "辛苦", "好的", "收到", "嗯嗯", "是的", "对", "👍", "👌", "😊", "😄"
        }) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * LLM 分类：输出 {intent: "CHAT" | "TASK"}。
     */
    private String classifyByLlm(String text) {
        String prompt = """
                你是群聊消息意图分类器。判断用户这条群聊消息是「闲聊」还是「需要多 Agent 协作的复杂任务」。

                判定规则：
                - 纯闲聊、寒暄、问候、情绪表达、感谢、打招呼、简单问答、随口感叹 → CHAT
                - 需要动手做事：调研/写文档/生成代码/数据分析/制定方案/多步骤或多角色协作/涉及工具执行 → TASK
                - 仅回复"收到/好的/嗯"等确认性短句 → CHAT
                - 拿不准的，倾向 CHAT（宁可简单对话，也不要对闲聊做任务编排）

                用户消息：
                %s

                请仅输出 JSON：{"intent": "CHAT"} 或 {"intent": "TASK"}
                """.formatted(text);

        ChatModel model = agentComponentFactory.createChatModel();
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build());
        String content = response.aiMessage().text();
        if (content == null || content.isBlank()) {
            return INTENT_CHAT;
        }

        // 提取 JSON
        String cleaned = content.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        IntentJson json = JsonUtils.toObj(cleaned, IntentJson.class);
        if (json != null && json.getIntent() != null) {
            return json.getIntent().toUpperCase().contains("TASK") ? INTENT_TASK : INTENT_CHAT;
        }
        // 兜底：文本包含 TASK 关键词则视为 TASK
        return content.toUpperCase().contains("TASK") ? INTENT_TASK : INTENT_CHAT;
    }

    /** 分类结果 DTO */
    @Data
    public static class IntentJson {
        private String intent;
    }
}
