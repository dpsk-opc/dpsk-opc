package com.xiaomizhou.dpsk.memory.config;

import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.memory.MemorySystem;
import com.xiaomizhou.dpsk.memory.PersonaProvider;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.generator.SummaryGenerator;
import com.xiaomizhou.dpsk.memory.impl.JVectorEmbeddingStoreImpl;
import com.xiaomizhou.dpsk.memory.impl.LocalEmbeddingClient;
import com.xiaomizhou.dpsk.memory.impl.SummaryGeneratorImpl;
import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import com.xiaomizhou.dpsk.memory.repository.MemorySummaryRepository;
import com.xiaomizhou.dpsk.memory.repository.MessageRepository;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

/**
 * 记忆系统 Spring 配置。
 * <p>
 * 根据环境变量和配置决定启用 L0/L0+L1/L0+L1+L2 不同模式。
 * <ul>
 *   <li>默认启用 L0（工作记忆，总是启用）</li>
 *   <li>L1（摘要记忆）默认启用</li>
 *   <li>L2（语义记忆，纯 RAG）默认启用</li>
 *   <li>L3（知识库 RAG）默认启用，与 L2 共用向量库</li>
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

    // ======================== L2 组件（向量存储 + EmbeddingClient） ========================

    @Bean
    public EmbeddingStore embeddingStore(LongTermFactRepository factRepository,
                                          FactManager.EmbeddingClient embeddingClient,
                                          ExecutorService executorService,
                                          @Value("${com.xiaomizhou.dpsk.opc.rag.jvector.data-path:./data/jvector}") String jvectorDataPath
    ) {
        // 使用 JVector 向量存储（ANN + 磁盘持久化 + 异步恢复历史数据）
        return new JVectorEmbeddingStoreImpl(
                embeddingClient.getDimension(),
                jvectorDataPath,
                factRepository,
                embeddingClient,
                executorService);
    }

    @Bean
    public FactManager.EmbeddingClient embeddingClient() {
        return new LocalEmbeddingClient();
    }

    // ======================== MemorySystem 主 Bean ========================

    @Bean
    public MemorySystem memorySystem(MessageRepository messageRepository,
                                     MemorySummaryRepository summaryRepository,
                                     SummaryGenerator summaryGenerator,
                                     EmbeddingStore embeddingStore,
                                     FactManager.EmbeddingClient embeddingClient,
                                     ToolRegistry toolRegistry,
                                     AgentDefProvider agentDefProvider,
                                     ExecutorService executorService) {
        log.info("Building MemorySystem with L0+L1+L2 (full stack, pure RAG)");
        return MemorySystem.builder()
                .messageRepository(messageRepository)
                .summaryRepository(summaryRepository)
                .summaryGenerator(summaryGenerator)
                .embeddingStore(embeddingStore)
                .embeddingClient(embeddingClient)
                .executorService(executorService)
                .agentDefProvider(agentDefProvider)
                .toolRegistry(toolRegistry)
                .build();
    }

    // ======================== ContextAssembler（暴露给 ChatService） ========================

    @Bean
    public ContextAssembler contextAssembler(MemorySystem memorySystem) {
        return memorySystem.getContextAssembler();
    }
}
