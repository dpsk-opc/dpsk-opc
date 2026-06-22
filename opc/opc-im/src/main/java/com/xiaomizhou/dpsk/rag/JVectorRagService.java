package com.xiaomizhou.dpsk.rag;

import com.xiaomizhou.dpsk.memory.manager.FactManager.EmbeddingClient;
import com.xiaomizhou.dpsk.rag.model.Document;
import com.xiaomizhou.dpsk.rag.model.RagFilter;
import com.xiaomizhou.dpsk.rag.model.RagHit;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 基于 JVector 的 RAG 服务实现，支持多 namespace 物理隔离。
 * <p>
 * 每个 {@link RagNamespace} 拥有独立的 {@link JVectorEmbeddingStore} 实例，
 * 持久化到不同子目录（如 {@code basePath/memory/}, {@code basePath/tool/}）。
 * <p>
 * 通过 {@link RagFilter} 支持 equals / in / notEquals / gte / lte / contains 等多种过滤条件。
 * 简单条件（equals-only）利用 JVector metadata 预过滤；复杂条件走检索后过滤。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Slf4j
public class JVectorRagService implements RagService {

    private final EmbeddingClient embeddingClient;
    private final String basePath;
    private final int dimension;

    /** namespace -> JVector store */
    private final Map<RagNamespace, JVectorEmbeddingStore> stores = new ConcurrentHashMap<>();

    /** namespace -> 读写锁 */
    private final Map<RagNamespace, ReadWriteLock> locks = new ConcurrentHashMap<>();

    private volatile boolean shutdown = false;

    /**
     * @param embeddingClient 向量化客户端
     * @param basePath        JVector 数据根目录（如 ./data/jvector）
     * @param dimension       向量维度
     */
    public JVectorRagService(EmbeddingClient embeddingClient, String basePath, int dimension) {
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient must not be null");
        this.basePath = basePath != null && !basePath.isEmpty() ? basePath : "./data/jvector";
        this.dimension = dimension;
        log.info("JVectorRagService initialized: basePath={}, dimension={}", this.basePath, dimension);
    }

    // ==================== 数据写入 ====================

    @Override
    public String addDocument(RagNamespace namespace, Document document) {
        checkShutdown();
        if (document == null || document.getText() == null || document.getText().isBlank()) {
            log.warn("Skip add: empty document");
            return "";
        }

        String id = (document.getId() != null && !document.getId().isBlank())
                ? document.getId()
                : UUID.randomUUID().toString().replace("-", "");

        List<Float> vector = embeddingClient.embed(document.getText());
        if (CollectionUtils.isEmpty(vector)) {
            log.warn("Skip add: embedding returned empty for id={}", id);
            return id;
        }

        JVectorEmbeddingStore store = getOrCreateStore(namespace);
        ReadWriteLock lock = getLock(namespace);

        lock.writeLock().lock();
        try {
            TextSegment segment = TextSegment.from(document.getText(), Metadata.from(document.getMetadata()));
            store.add(Embedding.from(vector), segment);
            store.save();
            log.debug("Added document to {}: id={}", namespace, id);
        } finally {
            lock.writeLock().unlock();
        }

        return id;
    }

    @Override
    public List<String> addDocuments(RagNamespace namespace, List<Document> documents) {
        checkShutdown();
        if (CollectionUtils.isEmpty(documents)) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            ids.add(addDocument(namespace, doc));
        }
        return ids;
    }

    // ==================== 数据检索 ====================

    @Override
    public List<RagHit> search(RagNamespace namespace, String query, int topK, double minScore) {
        return search(namespace, query, topK, minScore, null);
    }

    @Override
    public List<RagHit> search(RagNamespace namespace, String query, int topK, double minScore, RagFilter filter) {
        checkShutdown();
        if (query == null || query.isBlank()) return List.of();

        List<Float> queryVector = embeddingClient.embed(query);
        if (CollectionUtils.isEmpty(queryVector)) return List.of();

        JVectorEmbeddingStore store = getOrCreateStore(namespace);
        ReadWriteLock lock = getLock(namespace);

        lock.readLock().lock();
        try {
            Embedding queryEmbedding = Embedding.from(queryVector);

            // 预取更多结果，为后过滤留余量
            int fetchSize = (filter != null && !filter.isEmpty()) ? topK * 3 : topK;

            EmbeddingSearchResult<TextSegment> result = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(fetchSize)
                            .minScore(minScore)
                            .build());

            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            if (CollectionUtils.isEmpty(matches)) return List.of();

            return matches.stream()
                    .filter(m -> m.score() >= minScore)
                    .map(m -> {
                        TextSegment seg = m.embedded();
                        Map<String, String> meta = toMap(seg.metadata());
                        return RagHit.builder()
                                .score(m.score())
                                .id(m.embeddingId())
                                .text(seg.text())
                                .metadata(meta)
                                .build();
                    })
                    .filter(hit -> filter == null || filter.isEmpty() || filter.matches(hit.getMetadata()))
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(topK)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== 数据管理 ====================

    @Override
    public void deleteById(RagNamespace namespace, String id) {
        checkShutdown();
        if (id == null || id.isBlank()) return;

        JVectorEmbeddingStore store = stores.get(namespace);
        if (store == null) return;

        ReadWriteLock lock = getLock(namespace);
        lock.writeLock().lock();
        try {
            store.remove(id);
            log.debug("Deleted from {}: id={}", namespace, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int deleteByFilter(RagNamespace namespace, Map<String, String> filter) {
        checkShutdown();
        if (filter == null || filter.isEmpty()) return 0;

        // JVector 不支持按 metadata 批量删除，需要先搜后删
        // 这里采用一个折中方案：搜索一批结果后逐个删除
        // 注意：此实现有局限性，适合小批量删除场景
        log.warn("deleteByFilter is an expensive operation for JVector. "
                + "Consider using clearNamespace if you need to remove all data for a namespace.");

        // 使用一个虚拟向量做全量扫描（minScore=0 获取所有）
        List<Float> dummyVector = new ArrayList<>();
        for (int i = 0; i < dimension; i++) dummyVector.add(0.0f);

        JVectorEmbeddingStore store = stores.get(namespace);
        if (store == null) return 0;

        ReadWriteLock lock = getLock(namespace);
        lock.writeLock().lock();
        try {
            EmbeddingSearchResult<TextSegment> result = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(Embedding.from(dummyVector))
                            .maxResults(10000)
                            .minScore(-1.0)  // 不过滤分数
                            .build());

            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            if (CollectionUtils.isEmpty(matches)) return 0;

            int deleted = 0;
            for (EmbeddingMatch<TextSegment> m : matches) {
                Map<String, String> meta = toMap(m.embedded().metadata());
                if (matchesFilter(meta, filter)) {
                    store.remove(m.embeddingId());
                    deleted++;
                }
            }
            if (deleted > 0) {
                log.info("Deleted {} documents from {} by filter", deleted, namespace);
            }
            return deleted;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long count(RagNamespace namespace) {
        return count(namespace, null);
    }

    @Override
    public long count(RagNamespace namespace, Map<String, String> filter) {
        checkShutdown();
        JVectorEmbeddingStore store = stores.get(namespace);
        if (store == null) return 0;

        ReadWriteLock lock = getLock(namespace);
        lock.readLock().lock();
        try {
            List<Float> dummyVector = new ArrayList<>();
            for (int i = 0; i < dimension; i++) dummyVector.add(0.0f);

            EmbeddingSearchResult<TextSegment> result = store.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(Embedding.from(dummyVector))
                            .maxResults(Integer.MAX_VALUE)
                            .minScore(-1.0)
                            .build());

            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            if (CollectionUtils.isEmpty(matches)) return 0;

            if (filter == null || filter.isEmpty()) {
                return matches.size();
            }

            return matches.stream()
                    .filter(m -> matchesFilter(toMap(m.embedded().metadata()), filter))
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public void clearNamespace(RagNamespace namespace) {
        checkShutdown();
        JVectorEmbeddingStore store = stores.remove(namespace);
        locks.remove(namespace);
        if (store != null) {
            ReadWriteLock lock = getLock(namespace);
            lock.writeLock().lock();
            try {
//                store.close();
                log.info("Cleared namespace: {}", namespace);
            } catch (Exception e) {
                log.error("Failed to close store for namespace: {}", namespace, e);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return !shutdown && embeddingClient != null;
    }

    @Override
    public void shutdown() {
        shutdown = true;
        stores.values().forEach(store -> {
            try {
//                store.close();
            } catch (Exception e) {
                log.error("Failed to close JVector store", e);
            }
        });
        stores.clear();
        locks.clear();
        log.info("JVectorRagService shutdown complete");
    }

    // ==================== 内部方法 ====================

    private void checkShutdown() {
        if (shutdown) {
            throw new IllegalStateException("JVectorRagService has been shut down");
        }
    }

    /**
     * 获取或懒创建指定 namespace 的 JVector 存储实例。
     */
    private JVectorEmbeddingStore getOrCreateStore(RagNamespace namespace) {
        return stores.computeIfAbsent(namespace, ns -> {
            Path dir = Paths.get(basePath, ns.getDirName());
            JVectorEmbeddingStore store = JVectorEmbeddingStore.builder()
                    .dimension(dimension)
                    .persistencePath(dir.toString())
                    .build();
            log.info("Created JVector store for namespace '{}': path={}", ns, dir);
            return store;
        });
    }

    private ReadWriteLock getLock(RagNamespace namespace) {
        return locks.computeIfAbsent(namespace, k -> new ReentrantReadWriteLock());
    }

    private static Map<String, String> toMap(Metadata metadata) {
        Map<String, String> map = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.toMap().forEach((k, v) -> map.put(k, v != null ? v.toString() : ""));
        }
        return map;
    }

    private static boolean matchesFilter(Map<String, String> meta, Map<String, String> filter) {
        if (filter == null || filter.isEmpty()) return true;
        for (Map.Entry<String, String> e : filter.entrySet()) {
            String val = meta.get(e.getKey());
            if (!e.getValue().equals(val)) return false;
        }
        return true;
    }
}
