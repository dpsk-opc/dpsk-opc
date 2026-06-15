package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.memory.manager.FactManager;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/6/2 16:37
 * @description
 */
@Slf4j
public class LocalEmbeddingClient implements FactManager.EmbeddingClient {

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    @Override
    public List<Float> embed(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        Embedding embedding = response.content();
        return embedding.vectorAsList();
    }

    @Override
    public int getDimension() {
        return embeddingModel.dimension();
    }
}
