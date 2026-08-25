package com.xiaomizhou.dpsk.memory.store;

import dev.langchain4j.data.document.Metadata;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 向量存储接口，用于 L2 语义检索。
 * <p>
 * 具体实现可以是 JVector、Milvus 等向量数据库。
 * opc-im 负责提供具体实现并注入给 FactManager。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public interface EmbeddingStore {

    /**
     * 添加一条向量化的文本（自动生成 id）。
     *
     * @param vector   向量数据
     * @param text     原始文本
     * @param metadata 元数据（type, owner_code, target_code, conversation_code, timestamp, importance）
     * @return 存储的 id
     */
    String add(List<Float> vector, String text, Map<String, String> metadata);

    /**
     * 添加一条向量化的文本（指定 id）。
     *
     * @param id       唯一标识
     * @param vector   向量数据
     * @param text     原始文本
     * @param metadata 元数据
     * @return 存储的 id
     */
    default String add(String id, List<Float> vector, String text, Map<String, String> metadata) {
        return add(vector, text, metadata);
    }

    /**
     * 语义相似度检索。
     *
     * @param queryVector 查询向量
     * @param maxResults  最大返回数
     * @param minScore    最低相似度阈值
     * @return 匹配结果列表
     */
    List<EmbeddingMatch> search(List<Float> queryVector, int maxResults, double minScore);

    /**
     * 带 metadata 过滤的语义相似度检索。
     * <p>
     * 只返回 metadata 中 key-value 完全匹配的结果。用于 L3 知识库检索（过滤 type=knowledge）。
     *
     * @param queryVector    查询向量
     * @param maxResults     最大返回数
     * @param minScore       最低相似度阈值
     * @param metadataFilter metadata 过滤条件（key -> value，必须全部匹配）
     * @return 匹配结果列表
     */
    default List<EmbeddingMatch> search(List<Float> queryVector, int maxResults, double minScore,
                                        Map<String, String> metadataFilter) {
        // 默认实现：检索后过滤（子类可重写以优化性能）
        List<EmbeddingMatch> all = search(queryVector, maxResults * 2, minScore);
        if (metadataFilter == null || metadataFilter.isEmpty()) {
            return all;
        }
        return all.stream()
                .filter(m -> matchesFilter(m, metadataFilter))
                .limit(maxResults)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 检查 EmbeddingMatch 的 metadata 是否匹配所有过滤条件
     */
    default boolean matchesFilter(EmbeddingMatch match, Map<String, String> filter) {
        if (filter == null || filter.isEmpty()) return true;
        for (Map.Entry<String, String> e : filter.entrySet()) {
            String actual = match.getMetadata().getString(e.getKey());
            if (Objects.isNull(e.getValue()) || !e.getValue().equals(actual)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 删除指定 id 的向量。
     *
     * @param id 向量记录 id
     */
    void delete(String id);

    /**
     * 向量检索匹配结果。
     */
    @Data
    @AllArgsConstructor
    class EmbeddingMatch {
        private final double score;
        private final String id;
        private final String text;
        private final Metadata metadata;
    }
}
