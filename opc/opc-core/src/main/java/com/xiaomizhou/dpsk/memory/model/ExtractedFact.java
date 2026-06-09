package com.xiaomizhou.dpsk.memory.model;

/**
 * 从对话中提取的事实结果。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class ExtractedFact {

    /** 类型：PREFERENCE / EVENT / RELATION */
    private String type;

    /** 事实内容 */
    private String content;

    /** 重要性 0.0 ~ 1.0 */
    private double importance;

    public ExtractedFact() {
    }

    public ExtractedFact(String type, String content, double importance) {
        this.type = type;
        this.content = content;
        this.importance = importance;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public double getImportance() { return importance; }
    public void setImportance(double importance) { this.importance = importance; }

    @Override
    public String toString() {
        return "ExtractedFact{type='" + type + "', content='" + content + "', importance=" + importance + '}';
    }
}
