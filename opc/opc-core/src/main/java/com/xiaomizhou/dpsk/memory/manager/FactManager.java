package com.xiaomizhou.dpsk.memory.manager;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.model.MemoryFragment;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore;
import com.xiaomizhou.dpsk.memory.store.EmbeddingStore.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * L2 语义记忆管理器（纯 RAG 模式）。
 * <p>
 * 不再使用 LLM 提取事实，改为：
 * <ul>
 *   <li><b>存储</b>：消息移出 L0 窗口时，直接将原始消息文本向量化存入向量库</li>
 *   <li><b>检索</b>：按需语义检索 Top-K，不再做"全量注入 system prompt"</li>
 * </ul>
 * <p>
 * 与 L3 知识库共用同一个向量库和 embedding 模型，通过 metadata.type 区分（fact vs knowledge）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class FactManager {

    private final EmbeddingStore embeddingStore;
    private final EmbeddingClient embeddingClient;

    /**
     * @param embeddingStore  向量存储
     * @param embeddingClient 向量化客户端
     */
    public FactManager(EmbeddingStore embeddingStore,
                       EmbeddingClient embeddingClient) {
        this.embeddingStore = embeddingStore;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 将原始消息文本直接向量化并存入向量库。
     * <p>
     * 消息移出 L0 窗口时由 MemoryManager 调用，不再经过 LLM 提取。
     *
     * @param ownerCode        Agent 编码
     * @param targetCode       目标编码（单聊为用户，群聊为群组）
     * @param conversationCode 会话编码
     * @param messageTexts     被移出的消息文本列表
     */
    public void storeMessages(String ownerCode, String targetCode, String conversationCode,
                              List<String> messageTexts) {
        if (messageTexts == null || messageTexts.isEmpty()) {
            return;
        }
        if (embeddingStore == null || embeddingClient == null) {
            return;
        }

        for (String text : messageTexts) {
            if (text == null || text.isBlank()) continue;
            try {
                String code = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

                List<Float> vector = embeddingClient.embed(text);
                if (vector == null || vector.isEmpty()) {
                    log.warn("[FactManager] Embedding returned empty for message: {}",
                            text.substring(0, Math.min(50, text.length())));
                    continue;
                }

                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("type", "fact");
                metadata.put("owner_code", ownerCode);
                metadata.put("target_code", targetCode);
                metadata.put("conversation_code", conversationCode);
                metadata.put("timestamp", Instant.now().toString());

                embeddingStore.add(code, vector, text, metadata);
                log.debug("[FactManager] Stored message embedding: code={}", code);
            } catch (Exception e) {
                log.error("[FactManager] Failed to store message embedding: {}", text.substring(0, Math.min(50, text.length())), e);
            }
        }
    }

    /**
     * 语义检索历史消息（纯 RAG，按需检索 Top-K）。
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

            // 用 metadata filter 精确过滤 owner_code
            Map<String, String> filter = new LinkedHashMap<>();
            filter.put("type", "fact");
            filter.put("owner_code", ownerCode);

            List<EmbeddingMatch> matches = embeddingStore.search(
                    queryVector, k * 2, MemoryConfig.L2_RETRIEVAL_MIN_SCORE, filter);

            Instant now = Instant.now();
            return matches.stream()
                    .map(m -> {
                        Instant ts = parseInstantSafe(m.getMetadata().getString("timestamp"), now);
                        long days = ChronoUnit.DAYS.between(ts, now);
                        double decay = Math.exp(-MemoryConfig.L2_DECAY_LAMBDA * days);
                        double adjustedScore = m.getScore() * decay;
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

        /**
         * 获取向量维度。
         * @return 向量维度
         */
        int getDimension();
    }
}
