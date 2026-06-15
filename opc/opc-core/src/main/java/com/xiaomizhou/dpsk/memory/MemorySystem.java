package com.xiaomizhou.dpsk.memory;

import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.generator.SummaryGenerator;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.manager.KnowledgeManager;
import com.xiaomizhou.dpsk.memory.manager.MemoryManager;
import com.xiaomizhou.dpsk.memory.manager.SummaryManager;
import com.xiaomizhou.dpsk.memory.repository.MemorySummaryRepository;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.memory.store.DatabaseChatMemoryStore;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * 记忆系统构建器。
 * <p>
 * 提供简化的构建方法，按需组装 L0/L1/L2/L3 各层组件。
 * 支持四种模式：
 * <ul>
 *   <li><b>L0 Only</b>：仅工作记忆，适合简单场景</li>
 *   <li><b>L0 + L1</b>：工作记忆 + 摘要记忆</li>
 *   <li><b>L0 + L1 + L2</b>：工作记忆 + 摘要 + 语义记忆（纯 RAG）</li>
 *   <li><b>L0 + L1 + L2 + L3</b>：完整四级记忆（含知识库 RAG）</li>
 * </ul>
 * <p>
 * L2 已改为纯 RAG 模式：消息移出 L0 窗口时直接向量化原始文本，
 * 检索时按需语义检索 Top-K，不再使用 LLM 提取事实。
 *
 * <pre>{@code
 * // 示例：构建完整四级记忆系统
 * MemorySystem system = MemorySystem.builder()
 *     .messageRepository(messageRepo)      // 必填
 *     .summaryRepository(summaryRepo)      // L1 需要
 *     .summaryGenerator(summaryGen)        // L1 需要
 *     .embeddingStore(embeddingStore)      // L2 向量存储（可选）
 *     .embeddingClient(embeddingClient)    // L2/L3 向量化客户端（可选）
 *     .knowledgeEmbeddingStore(store)      // L3 知识库向量存储（可选，默认复用 embeddingStore）
 *     .knowledgeEmbeddingClient(client)    // L3 向量化客户端（可选，默认复用 embeddingClient）
 *     .build();
 *
 * // 使用
 * ContextAssembler assembler = system.getContextAssembler();
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemorySystem {

    private final ContextAssembler contextAssembler;
    private final SummaryManager summaryManager;
    private final FactManager factManager;
    private final KnowledgeManager knowledgeManager;
    private final MemoryManager memoryManager;

    private final MessageRepository messageRepository;

    private MemorySystem(Builder builder) {
        // 构建 L1（可选）
        SummaryManager sm = null;
        if (builder.summaryRepository != null && builder.summaryGenerator != null) {
            sm = new SummaryManager(builder.summaryRepository, builder.summaryGenerator);
        }

        // 构建 L2（可选）：纯 RAG 模式，只需要 embeddingStore 和 embeddingClient
        FactManager fm = null;
        if (builder.embeddingStore != null && builder.embeddingClient != null) {
            fm = new FactManager(builder.embeddingStore, builder.embeddingClient);
        }

        // 构建 L3（可选）：知识库 RAG
        // L3 可独立配置 store/client，默认复用 L2 的
        KnowledgeManager km = null;
        if (builder.knowledgeEmbeddingStore != null && builder.knowledgeEmbeddingClient != null) {
            km = new KnowledgeManager(builder.knowledgeEmbeddingStore, builder.knowledgeEmbeddingClient);
        } else if (builder.embeddingStore != null && builder.embeddingClient != null) {
            // 未单独配置 L3 时，复用 L2 的 store 和 client
            km = new KnowledgeManager(builder.embeddingStore, builder.embeddingClient);
        }

        // 构建 MemoryManager（L1+L2 都有时才创建完整编排器）
        MemoryManager mm = null;
        if (sm != null && fm != null) {
            if (builder.executorService != null) {
                mm = new MemoryManager(sm, fm, builder.executorService);
            } else {
                mm = new MemoryManager(sm, fm);
            }
        }

        // 构建 ContextAssembler（传入 L3 KnowledgeManager）
        this.contextAssembler = new ContextAssembler(builder.messageRepository, sm, fm, km);
        this.summaryManager = sm;
        this.factManager = fm;
        this.knowledgeManager = km;
        this.memoryManager = mm;
        this.messageRepository = builder.messageRepository;
    }

    public DatabaseChatMemoryStore getChatMemoryStore(ContextAssembler.AssembledPrompt prompt) {
        return new DatabaseChatMemoryStore(messageRepository, memoryManager, prompt);
    }

    public ContextAssembler getContextAssembler() {
        return contextAssembler;
    }

    public SummaryManager getSummaryManager() {
        return summaryManager;
    }

    public FactManager getFactManager() {
        return factManager;
    }

    public KnowledgeManager getKnowledgeManager() {
        return knowledgeManager;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * 关闭异步资源
     */
    public void shutdown() {
        if (memoryManager != null) {
            memoryManager.shutdown();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * MemorySystem 构建器。
     */
    public static class Builder {
        private MessageRepository messageRepository;
        private MemorySummaryRepository summaryRepository;
        private SummaryGenerator summaryGenerator;
        private EmbeddingStore embeddingStore;
        private FactManager.EmbeddingClient embeddingClient;
        private EmbeddingStore knowledgeEmbeddingStore;
        private FactManager.EmbeddingClient knowledgeEmbeddingClient;
        private ExecutorService executorService;

        /** 必填：消息仓储 */
        public Builder messageRepository(MessageRepository repo) {
            this.messageRepository = repo;
            return this;
        }

        /** L1：摘要仓储 */
        public Builder summaryRepository(MemorySummaryRepository repo) {
            this.summaryRepository = repo;
            return this;
        }

        /** L1：摘要生成器 */
        public Builder summaryGenerator(SummaryGenerator gen) {
            this.summaryGenerator = gen;
            return this;
        }

        /** L2：向量存储（可选） */
        public Builder embeddingStore(EmbeddingStore store) {
            this.embeddingStore = store;
            return this;
        }

        /** L2/L3：向量化客户端（可选） */
        public Builder embeddingClient(FactManager.EmbeddingClient client) {
            this.embeddingClient = client;
            return this;
        }

        /**
         * L3：知识库向量存储（可选，默认复用 embeddingStore）。
         * <p>
         * 如果 L3 与 L2 共用同一个 JVector 实例，则无需单独配置此方法。
         */
        public Builder knowledgeEmbeddingStore(EmbeddingStore store) {
            this.knowledgeEmbeddingStore = store;
            return this;
        }

        /**
         * L3：知识库向量化客户端（可选，默认复用 embeddingClient）。
         * <p>
         * 如果 L3 与 L2 使用同一个 embedding 模型，则无需单独配置此方法。
         */
        public Builder knowledgeEmbeddingClient(FactManager.EmbeddingClient client) {
            this.knowledgeEmbeddingClient = client;
            return this;
        }

        /** 自定义异步线程池（可选） */
        public Builder executorService(ExecutorService executor) {
            this.executorService = executor;
            return this;
        }

        public MemorySystem build() {
            Objects.requireNonNull(messageRepository, "messageRepository is required");
            return new MemorySystem(this);
        }
    }
}
