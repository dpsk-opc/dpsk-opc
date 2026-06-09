package com.xiaomizhou.dpsk.memory.model;

/**
 * 记忆片段，包含文本内容与相关性得分（已含重要性加权与时间衰减）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class MemoryFragment {

    /** 记忆文本 */
    private final String text;

    /** 调整后的得分 */
    private final double score;

    public MemoryFragment(String text, double score) {
        this.text = text;
        this.score = score;
    }

    public String getText() { return text; }

    public double getScore() { return score; }

    @Override
    public String toString() {
        return "MemoryFragment{text='" + text + "', score=" + score + '}';
    }
}
