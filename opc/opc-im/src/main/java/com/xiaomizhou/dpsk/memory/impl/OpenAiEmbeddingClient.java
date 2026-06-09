package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.memory.manager.FactManager;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Embedding 客户端，实现 FactManager.EmbeddingClient。
 * <p>
 * 使用 OpenAI 兼容的 Embedding API 将文本转为向量。
 * 默认配置指向 OpenAI text-embedding-3-small 模型（可通过环境变量覆盖）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Slf4j
public class OpenAiEmbeddingClient implements FactManager.EmbeddingClient {

    private final OpenAiEmbeddingModel embeddingModel;

    /**
     * 使用默认配置创建（OpenAI text-embedding-3-small）。
     */
    public OpenAiEmbeddingClient() {
        this("text-embedding-3-small",
                "https://api.openai.com/v1",
                System.getenv().getOrDefault("OPENAI_API_KEY", "sk-0803dabfa90b4a188e116e007f442a62"));
    }

    /**
     * 使用自定义配置创建。
     *
     * @param modelName 模型名称
     * @param baseUrl   API 基础地址
     * @param apiKey    API 密钥
     */
    public OpenAiEmbeddingClient(String modelName, String baseUrl, String apiKey) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .modelName(modelName)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .logRequests(true)
                .logResponses(true)
                .build();
        log.info("OpenAiEmbeddingClient initialized: model={}, baseUrl={}", modelName, baseUrl);
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            float[] vector = embedding.vector();
            List<Float> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add(v);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to embed text: {}", text, e);
            return List.of();
        }
    }
}
