package com.xiaomizhou.dpsk.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 文档模型 —— 描述一个 Skill 的完整信息，用于写入 RAG 索引。
 * <p>
 * 包含完整描述 + 多个检索锚点（searchTexts / keywords / triggerQuestions），
 * 支持多粒度索引以提升检索召回率。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDoc {

    /** Skill 唯一标识 */
    private String skillId;

    /** Skill 名称 */
    private String skillName;

    /** Skill 完整描述（作为检索结果的 payload） */
    private String fullDescription;

    /**
     * 检索锚点文本列表（短文本，专门用于语义匹配）。
     * <p>
     * 每条 searchText 会作为独立文档写入向量库。
     * 例如：["创建定时任务并设置消息推送", "每天定时发送提醒", "定时执行HTTP请求"]
     */
    @Builder.Default
    private List<String> searchTexts = new ArrayList<>();

    /**
     * 关键词列表。
     * <p>
     * 所有关键词会拼接为一个文档写入向量库。
     * 例如：["定时", "cron", "调度", "周期任务"]
     */
    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    /**
     * 典型触发问题列表。
     * <p>
     * 每条问题会作为独立文档写入向量库，模拟用户可能的提问方式。
     * 例如：["怎么设置每天8点发消息？", "如何创建定时任务？"]
     */
    @Builder.Default
    private List<String> triggerQuestions = new ArrayList<>();

    /**
     * 业务元数据（status, owner_code, visible_to, category 等）
     */
    @Builder.Default
    private Map<String, String> metadata = new LinkedHashMap<>();

    // ---- 便捷方法 ----

    public SkillDoc addSearchText(String text) {
        this.searchTexts.add(text);
        return this;
    }

    public SkillDoc addKeyword(String keyword) {
        this.keywords.add(keyword);
        return this;
    }

    public SkillDoc addTriggerQuestion(String question) {
        this.triggerQuestions.add(question);
        return this;
    }

    public SkillDoc addMeta(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }
}
