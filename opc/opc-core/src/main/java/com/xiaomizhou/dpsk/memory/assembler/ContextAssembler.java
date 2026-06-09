package com.xiaomizhou.dpsk.memory.assembler;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.config.MemoryKey;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.manager.SummaryManager;
import com.xiaomizhou.dpsk.memory.model.LongTermFact;
import com.xiaomizhou.dpsk.memory.model.MemoryFragment;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 上下文组装引擎。
 * <p>
 * 按照 L2(长期事实) → L1(摘要) → @引用 → L2(语义检索) → L0(工作记忆) → 当前消息 的顺序
 * 组装最终发给 LLM 的 Prompt。
 * <p>
 * 无状态设计，每次调用 assemble() 都会实时查询各层记忆。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class ContextAssembler {

    private final MessageRepository messageRepository;
    private final SummaryManager summaryManager;
    private final FactManager factManager;

    public ContextAssembler(MessageRepository messageRepository,
                            SummaryManager summaryManager,
                            FactManager factManager) {
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
        this.summaryManager = summaryManager;
        this.factManager = factManager;
    }


    /**
     * 组装完整上下文 Prompt。
     *
     * @param systemPrompt     Agent 人设（system prompt）
     * @param userContent      用户当前消息内容
     * @param userCode         用户编码
     * @param agentCode        Agent 编码
     * @param conversationCode 会话编码
     * @param quoteMessageCode 引用消息编码（可为 null）
     * @return 组装后的 Prompt 文本
     */
    public AssembledPrompt assemble(String systemPrompt,
                                    String userContent,
                                    String userCode,
                                    String agentCode,
                                    String conversationCode,
                                    String quoteMessageCode) {
        StringBuilder systemPart = new StringBuilder();
        StringBuilder historyPart = new StringBuilder();

        // === System 部分 ===

        // 1. Agent 人设
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            systemPart.append(systemPrompt).append("\n");
        }

        // 2. L2 长期结构化事实（直接注入 system prompt）
        if (factManager != null) {
            List<LongTermFact> facts = factManager.getFacts(agentCode, userCode);
            if (!facts.isEmpty()) {
                systemPart.append("[你对用户的长期记忆]\n");
                for (LongTermFact fact : facts) {
                    systemPart.append("- ").append(fact.getFactContent()).append("\n");
                }
                systemPart.append("\n");
            }
        }

        // === History 部分 ===

        // 3. L1 摘要（若有）
        if (summaryManager != null) {
            String summary = summaryManager.getLatestSummary(conversationCode, agentCode);
            if (summary != null && !summary.isEmpty()) {
                historyPart.append("[近期往事] ").append(summary).append("\n\n");
            }
        }

        // 4. @历史消息引用
        if (quoteMessageCode != null && !quoteMessageCode.isEmpty()) {
            ChatMessage quoted = messageRepository.findByCode(quoteMessageCode);
            if (quoted != null) {
                List<ChatMessage> context = messageRepository.findContext(
                        quoteMessageCode, MemoryConfig.QUOTE_CONTEXT_SIZE);
                historyPart.append("[被引用的对话记录]\n");
                for (ChatMessage m : context) {
                    historyPart.append(formatChatMessage(m)).append("\n");
                }
                historyPart.append("\n");
            }
        }

        // 5. L2 按需语义检索
        if (needRetrieve(userContent) && factManager != null) {
            List<MemoryFragment> retrieved = factManager.retrieveMemories(
                    userContent, agentCode, userCode, MemoryConfig.L2_RETRIEVAL_TOPK);
            if (!retrieved.isEmpty()) {
                historyPart.append("[你可能想起来的旧事]\n");
                for (MemoryFragment f : retrieved) {
                    historyPart.append("- ").append(f.getText()).append("\n");
                }
                historyPart.append("\n");
            }
        }

        // 6. L0 工作记忆（最近 N 轮对话）
        MemoryKey key = new MemoryKey(conversationCode, agentCode);
        List<ChatMessage> recentMessages = messageRepository.findTopByConversationAndAgent(
                conversationCode, agentCode, MemoryConfig.L0_MAX_MESSAGES);
        for (ChatMessage m : recentMessages) {
            historyPart.append(formatChatMessage(m)).append("\n");
        }

        // 7. 当前用户消息
        historyPart.append("用户: ").append(userContent);

        return new AssembledPrompt(systemPart.toString(), historyPart.toString());
    }



    private boolean needRetrieve(String userContent) {
        if (userContent == null) {
            return false;
        }
        for (String keyword : MemoryConfig.RETRIEVAL_TRIGGER_KEYWORDS) {
            if (userContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 仅注入 L2 长期事实到 system prompt（不包含 L0/L1 历史）。
     * <p>
     * 适用于群聊场景：群聊的 L0 历史由 MessageWindowChatMemory 管理，
     * L1 摘要通过 MemoryManager 异步生成，此处仅注入 L2 事实。
     *
     * @param systemPrompt Agent 人设
     * @param ownerCode    Agent 编码
     * @param targetCode   目标编码（单聊为用户编码，群聊为群组编码）
     * @return 增强后的 system prompt
     */
    public String enrichSystemPrompt(String systemPrompt, String ownerCode, String targetCode) {
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        if (factManager == null) {
            return systemPrompt;
        }

        StringBuilder sb = new StringBuilder(systemPrompt);
        List<LongTermFact> facts = factManager.getFacts(ownerCode, targetCode);
        if (!facts.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("[你对用户的长期记忆]\n");
            for (LongTermFact fact : facts) {
                sb.append("- ").append(fact.getFactContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String formatChatMessage(ChatMessage message) {
        if (message instanceof AiMessage) {
            return "AI: " + ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
            return "用户: " + ((UserMessage) message).singleText();
        } else if (message instanceof SystemMessage) {
            return "[System]: " + ((SystemMessage) message).text();
        }
        return message.type().name() + ": " + message;
    }



    public static class AssembledPrompt {

        private final String systemPart;

        private final String historyPart;

        public AssembledPrompt(String systemPart, String historyPart) {
            this.systemPart = systemPart;
            this.historyPart = historyPart;
        }

        public String getSystemPart() {
            return systemPart;
        }

        public String getHistoryPart() {
            return historyPart;
        }



        /**
         * 获取完整的 Prompt 文本（System + History 合并）
         *
         */
        public String getFullPrompt() {
            if (systemPart.isEmpty()) {
                return historyPart;
            }
            return systemPart + "\n" + historyPart;
        }
    }
}
