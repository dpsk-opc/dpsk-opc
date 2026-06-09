package com.xiaomizhou.dpsk.memory.manager;

import com.xiaomizhou.dpsk.memory.config.MemoryKey;
import com.xiaomizhou.dpsk.memory.generator.FactExtractor;
import com.xiaomizhou.dpsk.memory.model.ExtractedFact;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 记忆更新编排器。
 * <p>
 * 当 DatabaseChatMemoryStore 检测到消息被移出 L0 窗口时，
 * 异步触发 L1 摘要更新和 L2 事实提取流水线。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemoryManager {

    private final SummaryManager summaryManager;
    private final FactManager factManager;
    private final FactExtractor factExtractor;

    /** 异步执行器 - 简化版：单线程池，避免 Spring 依赖 */
    private final ExecutorService asyncExecutor;

    /**
     * 构造函数。
     *
     * @param summaryManager L1 摘要管理器
     * @param factManager    L2 事实管理器
     * @param factExtractor  事实提取器
     */
    public MemoryManager(SummaryManager summaryManager,
                         FactManager factManager,
                         FactExtractor factExtractor) {
        this.summaryManager = Objects.requireNonNull(summaryManager, "summaryManager must not be null");
        this.factManager = Objects.requireNonNull(factManager, "factManager must not be null");
        this.factExtractor = Objects.requireNonNull(factExtractor, "factExtractor must not be null");
        this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "memory-update-async");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 使用自定义线程池的构造函数。
     */
    public MemoryManager(SummaryManager summaryManager,
                         FactManager factManager,
                         FactExtractor factExtractor,
                         ExecutorService executorService) {
        this.summaryManager = Objects.requireNonNull(summaryManager, "summaryManager must not be null");
        this.factManager = Objects.requireNonNull(factManager, "factManager must not be null");
        this.factExtractor = Objects.requireNonNull(factExtractor, "factExtractor must not be null");
        this.asyncExecutor = Objects.requireNonNull(executorService, "executorService must not be null");
    }

    /**
     * 消息移出 L0 窗口的回调。
     * <p>
     * 异步执行，不阻塞主对话流程。
     *
     * @param key     记忆隔离键
     * @param evicted 被移出窗口的消息列表
     */
    public void onMessagesEvicted(MemoryKey key, List<ChatMessage> evicted) {
        if (evicted == null || evicted.isEmpty()) {
            return;
        }

        // 提取消息文本
        List<String> messageTexts = evicted.stream()
                .map(m -> {
                    if (m instanceof AiMessage) return ((AiMessage) m).text();
                    if (m instanceof UserMessage) return ((UserMessage) m).singleText();
                    return m.toString();
                })
                .collect(Collectors.toList());

        // 群聊使用复合 conversationCode，确保摘要按群组隔离
        String summaryConvCode = key.isGroupChat()
                ? key.getConversationCode() + ":group:" + key.getGroupCode()
                : key.getConversationCode();

        // 异步执行
        asyncExecutor.submit(() -> {
            try {
                // L1 摘要更新
                summaryManager.updateSummary(summaryConvCode, key.getOwnerCode(),
                        resolveAgentName(key), messageTexts);

                // L2 事实提取
                List<ExtractedFact> facts = factExtractor.extractFacts(messageTexts);
                if (!facts.isEmpty()) {
                    String targetCode = resolveTargetCode(key);
                    factManager.processFacts(key.getOwnerCode(), targetCode,
                            summaryConvCode, facts);
                }
            } catch (Exception e) {
                // 异步更新失败不阻塞主流程
                System.err.println("[MemoryManager] Async update failed: " + e.getMessage());
            }
        });
    }

    /**
     * 关闭异步执行器。
     */
    public void shutdown() {
        asyncExecutor.shutdown();
    }

    /**
     * 解析 Agent 名称（简化实现，可由 opc-im 覆盖）。
     */
    protected String resolveAgentName(MemoryKey key) {
        return key.getOwnerCode();
    }

    /**
     * 解析目标编码（简化实现，可由 opc-im 覆盖）。
     * <p>
     * 群聊时目标为 groupCode，单聊时目标为 conversationCode。
     */
    protected String resolveTargetCode(MemoryKey key) {
        if (key.isGroupChat()) {
            return key.getGroupCode();
        }
        return key.getConversationCode();
    }
}
