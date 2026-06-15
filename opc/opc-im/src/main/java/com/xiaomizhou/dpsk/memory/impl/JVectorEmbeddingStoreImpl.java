package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.memory.manager.FactManager;
import com.xiaomizhou.dpsk.memory.model.LongTermFact;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import dev.langchain4j.community.store.embedding.jvector.JVectorEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 基于 JVector 的向量存储实现（ANN + 磁盘持久化）。
 * <p>
 * 委托给 LangChain4j 封装的 {@link JVectorEmbeddingStore}，支持自动磁盘持久化。
 * 初始化时异步从数据库恢复历史向量数据。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class JVectorEmbeddingStoreImpl implements EmbeddingStore {

    /** 底层 LangChain4j JVector 存储（自带 ANN + 磁盘持久化） */
    private final JVectorEmbeddingStore store;


    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** 当前向量数量 */
    private final AtomicInteger count = new AtomicInteger(0);

    /** 是否已完成历史数据恢复 */
    private volatile boolean restored = false;

    private final ExecutorService executorService;


    /**
     * @param dimension        向量维度
     * @param persistencePath  磁盘持久化目录（为空则不持久化）
     * @param factRepository   事实仓储（用于恢复历史数据，可为 null）
     * @param embeddingClient  向量化客户端（用于恢复历史数据，可为 null）
     * @param executorService  线程池（用于异步恢复，可为 null）
     */
    public JVectorEmbeddingStoreImpl(int dimension,
                                     String persistencePath,
                                     LongTermFactRepository factRepository,
                                     FactManager.EmbeddingClient embeddingClient,
                                     ExecutorService executorService) {
        Path dir = (persistencePath != null && !persistencePath.isEmpty())
                ? Paths.get(persistencePath) : null;

        var builder = JVectorEmbeddingStore.builder()
                .dimension(dimension);
        if (dir != null) {
            builder.persistencePath(dir.toString());
        }
        this.store = builder.build();
        this.executorService = executorService;

        log.info("JVectorEmbeddingStoreImpl initialized: dimension={}, persistenceDir={}", dimension, dir);

        // 异步从数据库恢复历史数据
        if (factRepository != null && embeddingClient != null) {
            restoreFromDatabase(factRepository, embeddingClient);
        } else {
            this.restored = true;
            log.info("No factRepository/embeddingClient provided, skip history restore");
        }
    }

    /**
     * 异步从数据库恢复所有未删除的长期事实到向量存储。
     */
    private void restoreFromDatabase(LongTermFactRepository factRepository,
                                     FactManager.EmbeddingClient embeddingClient) {
        log.info("Starting async restore of historical facts from database...");
        try {
            List<LongTermFact> facts = factRepository.findAllUnEmbedding();
            if (facts == null || facts.isEmpty()) {
                log.info("No historical facts to restore");
                this.restored = true;
                return;
            }

            for (LongTermFact fact : facts) {
                executorService.execute(() -> {
                    try {
                        List<Float> vector = embeddingClient.embed(fact.getFactContent());
                        if (vector == null || vector.isEmpty()) {
                            log.warn("Failed to embed fact: code={}, content={}", fact.getCode(),
                                    fact.getFactContent().substring(0, Math.min(50, fact.getFactContent().length())));
                            return;
                        }

                        Map<String, String> metadata = buildMetadata(fact);
                        addInternal(vector, fact.getFactContent(), metadata);
                    } catch (Exception e) {
                        log.warn("Failed to restore fact: code={}, content={}", fact.getCode(),
                                fact.getFactContent().substring(0, Math.min(50, fact.getFactContent().length())), e);
                    }
                });

                factRepository.toBedEmbedding(fact.getCode());
            }
        } catch (Exception e) {
            log.error("Failed to restore historical facts from database", e);
        } finally {
            this.restored = true;
        }
    }

    private Map<String, String> buildMetadata(LongTermFact fact) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("type", "fact");
        metadata.put("owner_code", fact.getOwnerCode() != null ? fact.getOwnerCode() : "");
        metadata.put("target_code", fact.getTargetCode() != null ? fact.getTargetCode() : "");
        metadata.put("timestamp", fact.getCreateTime() != null ? fact.getCreateTime().toString() : "");
        metadata.put("importance", String.valueOf(fact.getImportance()));
        return metadata;
    }

    @Override
    public String add(List<Float> vector, String text, Map<String, String> metadata) {
        return addInternal(vector, text, metadata);
    }

    private String addInternal(List<Float> vector, String text, Map<String, String> metadata) {
        if (CollectionUtils.isEmpty(vector)) {
            return "";
        }


        lock.writeLock().lock();
        try {
            // 将 text 和 metadata 封装为 TextSegment 存入 JVector payload，实现持久化
            // 这样即使重启，search() 也能从 JVector 的磁盘文件中恢复 text 和 metadata
            TextSegment segment = TextSegment.from(text, Metadata.from(metadata));
            store.add(Embedding.from(vector), segment);
            // Save to disk
            store.save();
        } finally {
            lock.writeLock().unlock();
        }
        return "";
    }

    @Override
    public List<com.xiaomizhou.dpsk.memory.store.EmbeddingStore.EmbeddingMatch> search(
            List<Float> queryVector, int maxResults, double minScore) {

        if (CollectionUtils.isEmpty(queryVector)) {
            return List.of();
        }

        lock.readLock().lock();
        try {
            Embedding queryEmbedding = Embedding.from(queryVector);
            EmbeddingSearchResult<TextSegment> result = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(maxResults * 2)
                            .minScore(minScore)
                            .build());

            List<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>> matches = result.matches();
            if (matches == null || matches.isEmpty()) return List.of();

            return matches.stream()
                    .filter(m -> m.score() >= minScore)
                    .map(m -> {
                        TextSegment segment = m.embedded();
                        String text = segment.text();
                        Metadata meta = segment.metadata();
                        return new com.xiaomizhou.dpsk.memory.store.EmbeddingStore.EmbeddingMatch(
                                m.score(), m.embeddingId(), text, meta);
                    })
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(maxResults)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String id) {
        lock.writeLock().lock();
        try {
            store.remove(id);
            log.debug("Deleted embedding: id={}", id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 是否已完成历史数据恢复。
     */
    public boolean isRestored() {
        return restored;
    }
}
