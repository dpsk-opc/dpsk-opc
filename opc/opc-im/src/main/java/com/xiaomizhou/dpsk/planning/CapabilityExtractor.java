package com.xiaomizhou.dpsk.planning;

import com.xiaomizhou.dpsk.db.dto.UsageRecord;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 能力标签提取器：从 Agent 的 prompt 中提取 3~5 个能力标签（短词）。
 * <p>
 * 在保存 Agent（新增/编辑）时同步调用，结果回填到 t_agent.capabilities，
 * 供群聊 {@code GroupResponderPicker} 做能力匹配，以及后续意图识别/任务规划的联动。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityExtractor {

    private final LlmExecutor llmExecutor;

    /** 提取的标签数量上下界 */
    private static final int MIN_TAGS = 3;
    private static final int MAX_TAGS = 5;

    /**
     * 从 prompt 提取能力标签。
     *
     * @param prompt Agent 人设/职责描述
     * @return 3~5 个能力标签；prompt 为空或提取失败时返回空列表（不抛异常，不阻塞保存）
     */
    public List<String> extract(String prompt) {
        if (StringUtils.isBlank(prompt)) {
            return Collections.emptyList();
        }

        String llmPrompt = """
                你是 Agent 能力标签提取器。根据 Agent 的人设/职责/角色描述，归纳出 %d~%d 个能力标签。
                要求：
                - 每个标签是 2~8 字的短词，如"代码开发"、"数据分析"、"文案写作"、"翻译"、"PPT制作"
                - 标签必须能反映该 Agent 能做什么（能力/技能方向），不要用姓名、性格等无关词
                - 宁可少而准，不要空泛
                人设/职责描述：
                %s

                请仅输出 JSON：{"capabilities": ["标签1", "标签2", "标签3"]}
                """.formatted(MIN_TAGS, MAX_TAGS, prompt);

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from(llmPrompt))
                    .build();
            UsageRecord usage = UsageRecord.builder()
                    .agentCode("CAPABILITY_EXTRACTOR")
                    .usageType("CHAT")
                    .build();
            String content = llmExecutor.chat(request, usage);
            if (StringUtils.isBlank(content)) {
                return Collections.emptyList();
            }

            // 提取 JSON
            String cleaned = content.trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            CapabilityJson json = JsonUtils.toObj(cleaned, CapabilityJson.class);
            if (json != null && json.getCapabilities() != null && !json.getCapabilities().isEmpty()) {
                return json.getCapabilities();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("extract capabilities error, prompt={}", prompt, e);
            return Collections.emptyList();
        }
    }

    /** 提取结果 DTO */
    @Data
    public static class CapabilityJson {
        private List<String> capabilities;
    }
}
