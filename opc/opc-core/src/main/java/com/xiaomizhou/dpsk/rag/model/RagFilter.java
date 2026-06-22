package com.xiaomizhou.dpsk.rag.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * RAG 检索过滤器，支持多种匹配条件。
 * <p>
 * 所有条件之间为 AND 关系。
 * <p>
 * 典型用法：
 * <pre>{@code
 * RagFilter filter = RagFilter.builder()
 *     .equals("type", "tool")
 *     .equals("status", "enabled")
 *     .in("visible_to", Set.of("user_001", "role_admin"))
 *     .build();
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Data
public class RagFilter {

    /** 精确匹配条件（key = value），AND 关系 */
    private final Map<String, String> equals = new LinkedHashMap<>();

    /** IN 条件：key 的值必须在集合中 */
    private final Map<String, Set<String>> in = new LinkedHashMap<>();

    /** 排除条件：key != value */
    private final Map<String, String> notEquals = new LinkedHashMap<>();

    /** 大于等于：key 的值 >= value（字符串比较，适用于时间戳等） */
    private final Map<String, String> gte = new LinkedHashMap<>();

    /** 小于等于：key 的值 <= value（字符串比较） */
    private final Map<String, String> lte = new LinkedHashMap<>();

    /** 文本包含：key 的值包含 value */
    private final Map<String, String> contains = new LinkedHashMap<>();

    // ---- Builder ----

    public static RagFilterBuilder builder() {
        return new RagFilterBuilder();
    }

    public static RagFilter empty() {
        return new RagFilter();
    }

    public static RagFilter of(String key, String value) {
        RagFilterBuilder b = builder();
        b.equals(key, value);
        return b.build();
    }

    // ---- 便捷判断 ----

    /**
     * 是否为空（无需过滤）
     */
    public boolean isEmpty() {
        return equals.isEmpty() && in.isEmpty() && notEquals.isEmpty()
                && gte.isEmpty() && lte.isEmpty() && contains.isEmpty();
    }

    /**
     * 转为简单的 equals-only Map（兼容旧的 metadata filter 接口）
     */
    public Map<String, String> toSimpleFilter() {
        return new LinkedHashMap<>(equals);
    }

    /**
     * 检查一条数据的 metadata 是否匹配当前过滤器。
     * 用于不支持预过滤的存储实现做后过滤。
     */
    public boolean matches(Map<String, String> meta) {
        if (meta == null) return isEmpty();

        for (Map.Entry<String, String> e : equals.entrySet()) {
            if (!e.getValue().equals(meta.get(e.getKey()))) return false;
        }
        for (Map.Entry<String, Set<String>> e : in.entrySet()) {
            if (!e.getValue().contains(meta.get(e.getKey()))) return false;
        }
        for (Map.Entry<String, String> e : notEquals.entrySet()) {
            if (e.getValue().equals(meta.get(e.getKey()))) return false;
        }
        for (Map.Entry<String, String> e : gte.entrySet()) {
            String val = meta.get(e.getKey());
            if (val == null || val.compareTo(e.getValue()) < 0) return false;
        }
        for (Map.Entry<String, String> e : lte.entrySet()) {
            String val = meta.get(e.getKey());
            if (val == null || val.compareTo(e.getValue()) > 0) return false;
        }
        for (Map.Entry<String, String> e : contains.entrySet()) {
            String val = meta.get(e.getKey());
            if (val == null || !val.contains(e.getValue())) return false;
        }
        return true;
    }

    // ---- Lombok-style builder (手动实现以支持 put 风格) ----

    public static class RagFilterBuilder {
        private final Map<String, String> equals = new LinkedHashMap<>();
        private final Map<String, Set<String>> in = new LinkedHashMap<>();
        private final Map<String, String> notEquals = new LinkedHashMap<>();
        private final Map<String, String> gte = new LinkedHashMap<>();
        private final Map<String, String> lte = new LinkedHashMap<>();
        private final Map<String, String> contains = new LinkedHashMap<>();

        public RagFilterBuilder equals(String key, String value) { this.equals.put(key, value); return this; }
        public RagFilterBuilder equals(Map<String, String> map) { this.equals.putAll(map); return this; }
        public RagFilterBuilder in(String key, Set<String> values) { this.in.put(key, values); return this; }
        public RagFilterBuilder in(Map<String, Set<String>> map) { this.in.putAll(map); return this; }
        public RagFilterBuilder notEquals(String key, String value) { this.notEquals.put(key, value); return this; }
        public RagFilterBuilder notEquals(Map<String, String> map) { this.notEquals.putAll(map); return this; }
        public RagFilterBuilder gte(String key, String value) { this.gte.put(key, value); return this; }
        public RagFilterBuilder gte(Map<String, String> map) { this.gte.putAll(map); return this; }
        public RagFilterBuilder lte(String key, String value) { this.lte.put(key, value); return this; }
        public RagFilterBuilder lte(Map<String, String> map) { this.lte.putAll(map); return this; }
        public RagFilterBuilder contains(String key, String value) { this.contains.put(key, value); return this; }
        public RagFilterBuilder contains(Map<String, String> map) { this.contains.putAll(map); return this; }

        public RagFilter build() {
            return new RagFilter(
                    new LinkedHashMap<>(equals),
                    new LinkedHashMap<>(in),
                    new LinkedHashMap<>(notEquals),
                    new LinkedHashMap<>(gte),
                    new LinkedHashMap<>(lte),
                    new LinkedHashMap<>(contains)
            );
        }
    }

    // private constructor for builder
    private RagFilter(Map<String, String> equals, Map<String, Set<String>> in,
                      Map<String, String> notEquals, Map<String, String> gte,
                      Map<String, String> lte, Map<String, String> contains) {
        this.equals.putAll(equals);
        this.in.putAll(in);
        this.notEquals.putAll(notEquals);
        this.gte.putAll(gte);
        this.lte.putAll(lte);
        this.contains.putAll(contains);
    }

    private RagFilter() {}
}
