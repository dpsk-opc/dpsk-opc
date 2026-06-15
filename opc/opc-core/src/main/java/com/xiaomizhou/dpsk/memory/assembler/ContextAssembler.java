package com.xiaomizhou.dpsk.memory.assembler;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.manager.KnowledgeManager;
import com.xiaomizhou.dpsk.memory.manager.SummaryManager;
import com.xiaomizhou.dpsk.memory.model.MemoryFragment;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.utils.MemoryUtils;
import dev.langchain4j.data.message.*;

import java.util.List;
import java.util.Objects;

/**
 * 上下文组装引擎。
 * <p>
 * 按照 人设 → L3(知识库) → L1(摘要) → @引用 → L2(语义检索) → L0(工作记忆) → 当前消息 的顺序
 * 组装最终发给 LLM 的 Prompt。
 * <p>
 * L2 已改为纯 RAG 模式：不再"全量注入"长期事实到 system prompt，
 * 而是在 history 部分按需语义检索 Top-K 相关历史消息。
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
    private final KnowledgeManager knowledgeManager;

    public ContextAssembler(MessageRepository messageRepository,
                            SummaryManager summaryManager,
                            FactManager factManager,
                            KnowledgeManager knowledgeManager) {
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
        this.summaryManager = summaryManager;
        this.factManager = factManager;
        this.knowledgeManager = knowledgeManager;
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

        // 2. L3 知识库记忆（向量检索 + 分级注入）
        if (knowledgeManager != null) {
            KnowledgeManager.L3Result l3Result = knowledgeManager.retrieve(userContent, agentCode);
            if (l3Result.shouldInject()) {
                systemPart.append(l3Result.getInjectText());
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

        // 5. L2 语义检索（纯 RAG，按需检索 Top-K 历史消息）
        if (factManager != null) {
            List<MemoryFragment> retrieved = factManager.retrieveMemories(
                    userContent, agentCode, userCode, MemoryConfig.L2_RETRIEVAL_TOPK);
            if (!retrieved.isEmpty()) {
                historyPart.append("[相关历史消息]\n");
                for (MemoryFragment f : retrieved) {
                    historyPart.append("- ").append(f.getText()).append("\n");
                }
                historyPart.append("\n");
            }
        }

//        // 6. L0 工作记忆（最近 N 轮对话）
//        List<ChatMessage> recentMessages = messageRepository.findTopByConversationAndAgent(
//                conversationCode, agentCode, MemoryConfig.L0_MAX_MESSAGES);
//        for (ChatMessage m : recentMessages) {
//            historyPart.append(formatChatMessage(m)).append("\n");
//        }

//        // 7. 当前用户消息
//        historyPart.append("用户: ").append(userContent);

        return new AssembledPrompt(systemPart.toString(), historyPart.toString());
    }


    /**
     * 仅注入 L2 语义检索结果到 system prompt（不包含 L0/L1 历史）。
     * <p>
     * 适用于群聊场景：群聊的 L0 历史由 MessageWindowChatMemory 管理，
     * L1 摘要通过 MemoryManager 异步生成，此处仅注入 L2 检索结果。
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

        // 群聊场景：根据 targetCode 做一次 L2 语义检索
        StringBuilder sb = new StringBuilder(systemPrompt);
        List<MemoryFragment> retrieved = factManager.retrieveMemories(
                targetCode, ownerCode, targetCode, MemoryConfig.L2_RETRIEVAL_TOPK);
        if (!retrieved.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("[相关历史消息]\n");
            for (MemoryFragment f : retrieved) {
                sb.append("- ").append(f.getText()).append("\n");
            }
        }
        return sb.toString();
    }

    private String formatChatMessage(ChatMessage message) {
        if (message instanceof AiMessage) {
            return "AI: " + ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
           return MemoryUtils.toSingleContent((UserMessage) message);
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
         */
        public String getFullPrompt() {
            if (systemPart.isEmpty()) {
                return historyPart;
            }
            return systemPart + "\n" + historyPart;
        }
    }
}
