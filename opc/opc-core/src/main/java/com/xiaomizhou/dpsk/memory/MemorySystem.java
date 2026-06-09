package com.xiaomizhou.dpsk.memory;

import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.generator.FactExtractor;
import com.xiaomizhou.dpsk.memory.generator.SummaryGenerator;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.manager.MemoryManager;
import com.xiaomizhou.dpsk.memory.manager.SummaryManager;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import com.xiaomizhou.dpsk.memory.repository.MemorySummaryRepository;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.memory.store.DatabaseChatMemoryStore;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * 记忆系统构建器。
 * <p>
 * 提供简化的构建方法，按需组装 L0/L1/L2 各层组件。
 * 支持三种模式：
 * <ul>
 *   <li><b>L0 Only</b>：仅工作记忆，适合简单场景</li>
 *   <li><b>L0 + L1</b>：工作记忆 + 摘要记忆</li>
 *   <li><b>L0 + L1 + L2</b>：完整三级记忆</li>
 * </ul>
 *
 * <pre>{@code
 * // 示例：构建完整三级记忆系统
 * MemorySystem system = MemorySystem.builder()
 *     .messageRepository(messageRepo)      // 必填
 *     .summaryRepository(summaryRepo)      // L1 需要
 *     .summaryGenerator(summaryGen)        // L1 需要
 *     .factRepository(factRepo)            // L2 需要
 *     .factExtractor(factExt)              // L2 需要
 *     .embeddingStore(embeddingStore)      // L2 向量检索需要（可选）
 *     .embeddingClient(embeddingClient)    // L2 向量检索需要（可选）
 *     .build();
 *
 * // 使用
 * DatabaseChatMemoryStore store = system.getChatMemoryStore();
 * ContextAssembler assembler = system.getContextAssembler();
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemorySystem {

    private final DatabaseChatMemoryStore chatMemoryStore;
    private final ContextAssembler contextAssembler;
    private final SummaryManager summaryManager;
    private final FactManager factManager;
    private final MemoryManager memoryManager;

    private MemorySystem(Builder builder) {
        // 构建 L1（可选）
        SummaryManager sm = null;
        if (builder.summaryRepository != null && builder.summaryGenerator != null) {
            sm = new SummaryManager(builder.summaryRepository, builder.summaryGenerator);
        }

        // 构建 L2（可选）
        FactManager fm = null;
        if (builder.factRepository != null && builder.factExtractor != null) {
            fm = new FactManager(builder.factRepository, builder.factExtractor,
                    builder.embeddingStore, builder.embeddingClient);
        }

        // 构建 MemoryManager（L1+L2 都有时才创建完整编排器）
        MemoryManager mm = null;
        if (sm != null && fm != null) {
            if (builder.executorService != null) {
                mm = new MemoryManager(sm, fm, builder.factExtractor, builder.executorService);
            } else {
                mm = new MemoryManager(sm, fm, builder.factExtractor);
            }
        }

        // 构建 DatabaseChatMemoryStore
        this.chatMemoryStore = new DatabaseChatMemoryStore(builder.messageRepository, mm, builder.personaProvider);

        // 构建 ContextAssembler
        this.contextAssembler = new ContextAssembler(builder.messageRepository, sm, fm);

        this.summaryManager = sm;
        this.factManager = fm;
        this.memoryManager = mm;
    }

    public DatabaseChatMemoryStore getChatMemoryStore() {
        return chatMemoryStore;
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

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /** 关闭异步资源 */
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
        private LongTermFactRepository factRepository;
        private FactExtractor factExtractor;
        private EmbeddingStore embeddingStore;
        private FactManager.EmbeddingClient embeddingClient;
        private ExecutorService executorService;
        private PersonaProvider personaProvider;

        /** 必填：消息仓储 */
        public Builder messageRepository(MessageRepository repo) {
            this.messageRepository = repo;
            return this;
        }

        /** 可选：人设提供者，用于在 L0 消息前置 persona */
        public Builder personaProvider(PersonaProvider provider) {
            this.personaProvider = provider;
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

        /** L2：事实仓储 */
        public Builder factRepository(LongTermFactRepository repo) {
            this.factRepository = repo;
            return this;
        }

        /** L2：事实提取器 */
        public Builder factExtractor(FactExtractor extractor) {
            this.factExtractor = extractor;
            return this;
        }

        /** L2：向量存储（可选） */
        public Builder embeddingStore(EmbeddingStore store) {
            this.embeddingStore = store;
            return this;
        }

        /** L2：向量化客户端（可选） */
        public Builder embeddingClient(FactManager.EmbeddingClient client) {
            this.embeddingClient = client;
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
