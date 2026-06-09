package com.xiaomizhou.dpsk.memory.config;

import com.xiaomizhou.dpsk.memory.MemorySystem;
import com.xiaomizhou.dpsk.memory.PersonaProvider;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.generator.FactExtractor;
import com.xiaomizhou.dpsk.memory.generator.SummaryGenerator;
import com.xiaomizhou.dpsk.memory.impl.*;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import com.xiaomizhou.dpsk.memory.repository.MemorySummaryRepository;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import com.xiaomizhou.dpsk.memory.task.EmbeddingTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * 记忆系统 Spring 配置。
 * <p>
 * 根据环境变量和配置决定启用 L0/L0+L1/L0+L1+L2 不同模式。
 * <ul>
 *   <li>默认启用 L0（工作记忆，总是启用）</li>
 *   <li>L1（摘要记忆）默认启用</li>
 *   <li>L2（长期事实 + 向量检索）默认启用</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Configuration
@Slf4j
public class MemoryConfiguration {

    // ======================== L1 组件 ========================

    @Bean
    public SummaryGenerator summaryGenerator() {
        return new SummaryGeneratorImpl();
    }

    // ======================== L2 组件 ========================

    @Bean
    public FactExtractor factExtractor() {
        return new FactExtractorImpl();
    }

    @Bean
    public EmbeddingStore embeddingStore() {
        // 使用纯内存向量存储（余弦相似度）
//        return new InMemoryEmbeddingStore();
        return new JVectorEmbeddingStoreImpl();
    }

    @Bean
    public FactManager.EmbeddingClient embeddingClient() {
//        // 可根据环境变量配置 embedding 模型
        return new LocalEmbeddingClient();
    }

    // ======================== MemorySystem 主 Bean ========================

    @Bean
    public MemorySystem memorySystem(MessageRepository messageRepository,
                                      MemorySummaryRepository summaryRepository,
                                      SummaryGenerator summaryGenerator,
                                      LongTermFactRepository factRepository,
                                      FactExtractor factExtractor,
                                      EmbeddingStore embeddingStore,
                                      FactManager.EmbeddingClient embeddingClient,
                                      PersonaProvider personaProvider) {
        log.info("Building MemorySystem with L0+L1+L2 (full stack)");
        return MemorySystem.builder()
                .messageRepository(messageRepository)
                .summaryRepository(summaryRepository)
                .summaryGenerator(summaryGenerator)
                .factRepository(factRepository)
                .factExtractor(factExtractor)
                .embeddingStore(embeddingStore)
                .embeddingClient(embeddingClient)
                .personaProvider(personaProvider)
                .executorService(Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "memory-async");
                    t.setDaemon(true);
                    return t;
                }))
                .build();
    }

    // ======================== ContextAssembler（暴露给 ChatService） ========================

    @Bean
    public ContextAssembler contextAssembler(MemorySystem memorySystem) {
        return memorySystem.getContextAssembler();
    }

    // ======================== EmbeddingTask 独立异步任务 ========================

    @Bean(destroyMethod = "shutdown")
    public EmbeddingTask embeddingTask(LongTermFactRepository factRepository,
                                        FactManager.EmbeddingClient embeddingClient,
                                        EmbeddingStore embeddingStore) {
        EmbeddingTask task = new EmbeddingTask(factRepository, embeddingClient, embeddingStore);
        task.start();
        log.info("EmbeddingTask bean created and started");
        return task;
    }
}
