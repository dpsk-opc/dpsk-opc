package com.xiaomizhou.dpsk.memory.manager;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.model.MemoryFragment;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;

/**
 * L3 知识库记忆管理器。
 * <p>
 * 负责从向量库检索知识库构建的内容，并根据相似度分数分级处理：
 * <ul>
 *   <li>score >= HIGH：强约束注入（告诉 LLM 严格基于知识库回答）</li>
 *   <li>LOW <= score < HIGH：普通注入（作为参考）</li>
 *   <li>score < LOW：不注入</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/15
 */
@Slf4j
public class KnowledgeManager {

    private final EmbeddingStore embeddingStore;
    private final FactManager.EmbeddingClient embeddingClient;

    public KnowledgeManager(EmbeddingStore embeddingStore,
                            FactManager.EmbeddingClient embeddingClient) {
        this.embeddingStore = Objects.requireNonNull(embeddingStore, "embeddingStore must not be null");
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient must not be null");
    }

    /**
     * L3 知识库检索结果，包含分级信息和注入文本。
     */
    public static class L3Result {
        /** 置信度等级 */
        private final ConfidenceLevel level;
        /** 待注入的格式化文本（可能为 null，表示无需注入） */
        private final String injectText;

        public L3Result(ConfidenceLevel level, String injectText) {
            this.level = level;
            this.injectText = injectText;
        }

        public ConfidenceLevel getLevel() { return level; }
        public String getInjectText() { return injectText; }

        /** 是否需要注入到 prompt */
        public boolean shouldInject() {
            return injectText != null && !injectText.isEmpty();
        }
    }

    /** 置信度等级 */
    public enum ConfidenceLevel {
        /** 高置信度：强约束注入 */
        HIGH,
        /** 中等置信度：普通注入 */
        MEDIUM,
        /** 低置信度：不注入 */
        NONE
    }

    /**
     * 检索知识库内容并根据阈值分级。
     * <p>
     * 检索范围：metadata.type = "knowledge"，按 owner_code 过滤。
     *
     * @param query     用户查询文本
     * @param ownerCode Agent 编码（用于过滤知识库归属）
     * @return L3 检索结果
     */
    public L3Result retrieve(String query, String ownerCode) {
        if (query == null || query.isEmpty() || ownerCode == null || ownerCode.isEmpty()) {
            return new L3Result(ConfidenceLevel.NONE, null);
        }

        try {
            List<Float> queryVector = embeddingClient.embed(query);
            if (CollectionUtils.isEmpty(queryVector)) {
                return new L3Result(ConfidenceLevel.NONE, null);
            }

            // metadata 过滤：只检索 knowledge 类型、归属指定 owner_code 的内容
            Map<String, String> filter = new LinkedHashMap<>();
            filter.put("type", "knowledge");
            filter.put("owner_code", ownerCode);

            List<EmbeddingMatch> matches = embeddingStore.search(
                    queryVector,
                    MemoryConfig.L3_RETRIEVAL_TOPK,
                    MemoryConfig.L3_LOW_THRESHOLD,  // 低于此值的不返回
                    filter);

            if (CollectionUtils.isEmpty(matches)) {
                return new L3Result(ConfidenceLevel.NONE, null);
            }

            // 取最高分判断等级
            double topScore = matches.get(0).getScore();

            if (topScore >= MemoryConfig.L3_HIGH_THRESHOLD) {
                return buildHighConfidenceResult(matches);
            } else {
                return buildMediumConfidenceResult(matches);
            }

        } catch (Exception e) {
            log.error("[KnowledgeManager] L3 retrieval failed: query={}, ownerCode={}", query, ownerCode, e);
            return new L3Result(ConfidenceLevel.NONE, null);
        }
    }

    /**
     * 检索知识库内容（不区分 owner_code），用于全局知识库查询。
     */
    public L3Result retrieveGlobal(String query) {
        if (query == null || query.isEmpty()) {
            return new L3Result(ConfidenceLevel.NONE, null);
        }

        try {
            List<Float> queryVector = embeddingClient.embed(query);
            if (queryVector == null || queryVector.isEmpty()) {
                return new L3Result(ConfidenceLevel.NONE, null);
            }

            Map<String, String> filter = new LinkedHashMap<>();
            filter.put("type", "knowledge");

            List<EmbeddingMatch> matches = embeddingStore.search(
                    queryVector,
                    MemoryConfig.L3_RETRIEVAL_TOPK,
                    MemoryConfig.L3_LOW_THRESHOLD,
                    filter);

            if (matches.isEmpty()) {
                return new L3Result(ConfidenceLevel.NONE, null);
            }

            double topScore = matches.get(0).getScore();

            if (topScore >= MemoryConfig.L3_HIGH_THRESHOLD) {
                return buildHighConfidenceResult(matches);
            } else {
                return buildMediumConfidenceResult(matches);
            }

        } catch (Exception e) {
            log.error("[KnowledgeManager] L3 global retrieval failed: query={}", query, e);
            return new L3Result(ConfidenceLevel.NONE, null);
        }
    }

    private L3Result buildHighConfidenceResult(List<EmbeddingMatch> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append(MemoryConfig.L3_HIGH_CONFIDENCE_PREFIX).append("\n");
        for (EmbeddingMatch m : matches) {
            sb.append("- ").append(m.getText()).append("\n");
        }
        sb.append("\n");
        return new L3Result(ConfidenceLevel.HIGH, sb.toString());
    }

    private L3Result buildMediumConfidenceResult(List<EmbeddingMatch> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append(MemoryConfig.L3_MEDIUM_CONFIDENCE_PREFIX).append("\n");
        for (EmbeddingMatch m : matches) {
            sb.append("- ").append(m.getText()).append("\n");
        }
        sb.append("\n");
        return new L3Result(ConfidenceLevel.MEDIUM, sb.toString());
    }
}
