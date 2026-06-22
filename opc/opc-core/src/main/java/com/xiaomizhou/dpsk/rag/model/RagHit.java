package com.xiaomizhou.dpsk.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 检索命中结果。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHit {

    /** 相似度分数 (0.0 ~ 1.0) */
    private double score;

    /** 文档 ID */
    private String id;

    /** 原始文本 */
    private String text;

    /** 元数据 */
    @Builder.Default
    private Map<String, String> metadata = new LinkedHashMap<>();

    /**
     * 便捷获取 metadata 中的某个字段
     */
    public String getMeta(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}
