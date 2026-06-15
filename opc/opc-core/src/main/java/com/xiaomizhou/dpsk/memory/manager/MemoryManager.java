package com.xiaomizhou.dpsk.memory.manager;

import com.google.common.collect.Lists;
import com.xiaomizhou.dpsk.memory.config.MemoryKey;
import dev.langchain4j.data.message.*;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 记忆更新编排器。
 * <p>
 * 当 DatabaseChatMemoryStore 检测到消息被移出 L0 窗口时，
 * 异步触发 L1 摘要更新和 L2 消息向量化存储。
 * <p>
 * L2 不再使用 LLM 提取事实，改为直接将原始消息文本向量化存入向量库（纯 RAG）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemoryManager {

    private final SummaryManager summaryManager;
    private final FactManager factManager;

    /** 异步执行器 - 简化版：单线程池，避免 Spring 依赖 */
    private final ExecutorService asyncExecutor;

    /**
     * 构造函数。
     *
     * @param summaryManager L1 摘要管理器
     * @param factManager    L2 语义记忆管理器
     */
    public MemoryManager(SummaryManager summaryManager,
                         FactManager factManager) {
        this.summaryManager = Objects.requireNonNull(summaryManager, "summaryManager must not be null");
        this.factManager = Objects.requireNonNull(factManager, "factManager must not be null");
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
                         ExecutorService executorService) {
        this.summaryManager = Objects.requireNonNull(summaryManager, "summaryManager must not be null");
        this.factManager = Objects.requireNonNull(factManager, "factManager must not be null");
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

        if (CollectionUtils.isEmpty(evicted)) {
            return;
        }

        List<String> messageTexts = Lists.newArrayList();

        for (ChatMessage message : evicted) {
            if (message instanceof AiMessage) {
                messageTexts.add(((AiMessage) message).text());
            } else if (message instanceof UserMessage) {

                if (((UserMessage) message).hasSingleText()) {
                    messageTexts.add(((UserMessage) message).singleText());
                }

                List<Content> contents = ((UserMessage) message).contents();
                for (Content content : contents) {
                    if (!(content instanceof TextContent)) {
                        continue;
                    }
                    // 文本消息也统计embedding(包括文件内容）
                    messageTexts.add(((TextContent) content).text());
                }

            } else {
                messageTexts.add(message.toString());
            }
        }

        // 提取消息文本
//        List<String> messageTexts = evicted.stream()
//                .map(m -> {
//                    if (m instanceof AiMessage) return ((AiMessage) m).text();
//                    if (m instanceof UserMessage) return ((UserMessage) m).singleText();
//                    return m.toString();
//                })
//                .collect(Collectors.toList());

        // 群聊使用复合 conversationCode，确保摘要按群组隔离
        String summaryConvCode = key.isGroupChat()
                ? key.getConversationCode() + ":group:" + key.getGroupCode()
                : key.getConversationCode();

        // 异步执行
        asyncExecutor.submit(() -> {
            try {
                // L1 摘要更新（保留 LLM 提取，价值在"压缩"）
                summaryManager.updateSummary(summaryConvCode, key.getOwnerCode(),
                        resolveAgentName(key), messageTexts);

                // L2 语义记忆存储：直接向量化原始消息文本（纯 RAG，不用 LLM 提取）
                String targetCode = resolveTargetCode(key);
                factManager.storeMessages(key.getOwnerCode(), targetCode,
                        summaryConvCode, messageTexts);
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
