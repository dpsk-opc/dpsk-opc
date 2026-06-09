package com.xiaomizhou.dpsk.memory.manager;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.generator.FactExtractor;
import com.xiaomizhou.dpsk.memory.model.ExtractedFact;
import com.xiaomizhou.dpsk.memory.model.LongTermFact;
import com.xiaomizhou.dpsk.memory.model.MemoryFragment;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * L2 长期事实记忆管理器。
 * <p>
 * 负责事实提取、双重存储（关系表 + 向量库）、语义检索（带重要性加权与时间衰减）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class FactManager {

    private final LongTermFactRepository factRepository;
    private final FactExtractor factExtractor;
    private final EmbeddingStore embeddingStore;
    private final EmbeddingClient embeddingClient;

    /**
     * @param factRepository   事实关系表仓储
     * @param factExtractor    事实提取器
     * @param embeddingStore   向量存储
     * @param embeddingClient  向量化客户端
     */
    public FactManager(LongTermFactRepository factRepository,
                       FactExtractor factExtractor,
                       EmbeddingStore embeddingStore,
                       EmbeddingClient embeddingClient) {
        this.factRepository = Objects.requireNonNull(factRepository, "factRepository must not be null");
        this.factExtractor = Objects.requireNonNull(factExtractor, "factExtractor must not be null");
        this.embeddingStore = embeddingStore;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 处理提取到的事实（双重存储）。
     *
     * @param ownerCode        Agent 编码
     * @param targetCode       目标编码
     * @param conversationCode 会话编码
     * @param facts            提取的事实列表
     */
    public void processFacts(String ownerCode, String targetCode, String conversationCode,
                             List<ExtractedFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }

        for (ExtractedFact f : facts) {
            try {
                String code = "fact_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

                // 1. 存入关系表
                LongTermFact entity = new LongTermFact();
                entity.setCode(code);
                entity.setOwnerCode(ownerCode);
                entity.setTargetCode(targetCode);
                entity.setFactType(f.getType());
                entity.setFactContent(f.getContent());
                entity.setImportance((float) f.getImportance());
                entity.setCreateTime(Instant.now());
                entity.setLastAccessedTime(Instant.now());
                entity.setIsDeleted(0);
                factRepository.save(entity);

                // 2. 向量化并存入向量库
                if (embeddingStore != null && embeddingClient != null) {
                    List<Float> vector = embeddingClient.embed(f.getContent());
                    if (vector != null && !vector.isEmpty()) {
                        Map<String, String> metadata = new LinkedHashMap<>();
                        metadata.put("type", "fact");
                        metadata.put("owner_code", ownerCode);
                        metadata.put("target_code", targetCode);
                        metadata.put("conversation_code", conversationCode);
                        metadata.put("timestamp", Instant.now().toString());
                        metadata.put("importance", String.valueOf(f.getImportance()));
                        embeddingStore.add(code, vector, f.getContent(), metadata);
                    }
                }
            } catch (Exception e) {
                log.error("[FactManager] Failed to process fact!",e);
            }
        }
    }

    /**
     * 语义检索记忆（带重要性加权与时间衰减）。
     *
     * @param query      查询文本
     * @param ownerCode  Agent 编码
     * @param targetCode 目标编码
     * @param k          返回最大条数
     * @return 记忆片段列表（按调整后得分降序）
     */
    public List<MemoryFragment> retrieveMemories(String query, String ownerCode, String targetCode, int k) {
        if (embeddingStore == null || embeddingClient == null) {
            return List.of();
        }

        try {
            List<Float> queryVector = embeddingClient.embed(query);
            if (queryVector == null || queryVector.isEmpty()) {
                return List.of();
            }

            List<EmbeddingMatch> matches = embeddingStore.search(
                    queryVector, k * 2, MemoryConfig.L2_RETRIEVAL_MIN_SCORE);

            Instant now = Instant.now();
            return matches.stream()
                    .filter(m -> ownerCode.equals(m.getMetadata().get("owner_code")))
                    .map(m -> {
                        double importance = parseDoubleSafe(m.getMetadata().get("importance"), 0.5);
                        Instant ts = parseInstantSafe(m.getMetadata().get("timestamp"), now);
                        long days = ChronoUnit.DAYS.between(ts, now);
                        double decay = Math.exp(-MemoryConfig.L2_DECAY_LAMBDA * days);
                        double adjustedScore = m.getScore() * importance * decay;
                        return new MemoryFragment(m.getText(), adjustedScore);
                    })
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .limit(k)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[FactManager] Failed to retrieve memories", e);
            return List.of();
        }
    }

    /**
     * 获取指定 Agent 对某目标的结构化事实列表（用于直接注入 system prompt）。
     */
    public List<LongTermFact> getFacts(String ownerCode, String targetCode) {
        return factRepository.findByOwnerAndTarget(ownerCode, targetCode);
    }

    private static double parseDoubleSafe(String value, double defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Instant parseInstantSafe(String value, Instant defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 向量化客户端接口。
     * <p>
     * opc-im 基于 LangChain4j EmbeddingModel 提供实现。
     */
    public interface EmbeddingClient {
        /**
         * 将文本转为向量。
         *
         * @param text 输入文本
         * @return 向量
         */
        List<Float> embed(String text);
    }
}
