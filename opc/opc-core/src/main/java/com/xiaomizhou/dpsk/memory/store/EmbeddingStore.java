package com.xiaomizhou.dpsk.memory.store;

import java.util.List;
import java.util.Map;

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
     * 添加一条向量化的文本。
     *
     * @param id       唯一标识（与 t_long_term_fact.code 一致）
     * @param vector   向量数据
     * @param text     原始文本
     * @param metadata 元数据（type, owner_code, target_code, conversation_code, timestamp, importance）
     * @return 存储的 id
     */
    String add(String id, List<Float> vector, String text, Map<String, String> metadata);

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
     * 删除指定 id 的向量。
     *
     * @param id 向量记录 id
     */
    void delete(String id);

    /**
     * 向量检索匹配结果。
     */
    class EmbeddingMatch {
        private final double score;
        private final String id;
        private final String text;
        private final Map<String, String> metadata;

        public EmbeddingMatch(double score, String id, String text, Map<String, String> metadata) {
            this.score = score;
            this.id = id;
            this.text = text;
            this.metadata = metadata;
        }

        public double getScore() { return score; }
        public String getId() { return id; }
        public String getText() { return text; }
        public Map<String, String> getMetadata() { return metadata; }
    }
}
