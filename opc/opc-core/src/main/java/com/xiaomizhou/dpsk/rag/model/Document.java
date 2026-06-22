package com.xiaomizhou.dpsk.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 文档模型（写入用）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /** 文档 ID（可选，不传则自动生成） */
    private String id;

    /** 原始文本 */
    private String text;

    /** 元数据（类型、归属、状态、时间戳等） */
    @Builder.Default
    private Map<String, String> metadata = new LinkedHashMap<>();

    // ---- 便捷工厂方法 ----

    public static Document of(String text) {
        return Document.builder().text(text).build();
    }

    public static Document of(String id, String text) {
        return Document.builder().id(id).text(text).build();
    }

    public static Document of(String id, String text, Map<String, String> metadata) {
        return Document.builder().id(id).text(text).metadata(metadata).build();
    }

    public Document addMeta(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }
}
