package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 纯内存向量存储实现（余弦相似度）。
 * <p>
 * 不依赖任何外部向量库，适用于小规模 L2 记忆检索场景。
 * 数据量大时可替换为 JVector/Milvus 等专业向量数据库。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class InMemoryEmbeddingStore implements EmbeddingStore {

    /** 文档存储 */
    private final Map<String, DocEntry> store = new ConcurrentHashMap<>();

    private static class DocEntry {
        final String id;
        final float[] vector;
        final String text;
        final Map<String, String> metadata;

        DocEntry(String id, float[] vector, String text, Map<String, String> metadata) {
            this.id = id;
            this.vector = vector;
            this.text = text;
            this.metadata = new LinkedHashMap<>(metadata);
        }
    }

    @Override
    public String add(String id, List<Float> vector, String text, Map<String, String> metadata) {
        float[] arr = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            arr[i] = vector.get(i);
        }
        store.put(id, new DocEntry(id, arr, text, metadata));
        log.debug("Added embedding: id={}", id);
        return id;
    }

    @Override
    public List<EmbeddingMatch> search(List<Float> queryVector, int maxResults, double minScore) {
        float[] queryArr = new float[queryVector.size()];
        for (int i = 0; i < queryVector.size(); i++) {
            queryArr[i] = queryVector.get(i);
        }

        return store.values().stream()
                .map(entry -> {
                    double score = cosineSimilarity(queryArr, entry.vector);
                    return new EmbeddingMatch(score, entry.id, entry.text, entry.metadata);
                })
                .filter(m -> m.getScore() >= minScore)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        store.remove(id);
        log.debug("Deleted embedding: id={}", id);
    }

    /**
     * 获取存储的文档数量。
     */
    public int size() {
        return store.size();
    }

    /**
     * 计算余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
