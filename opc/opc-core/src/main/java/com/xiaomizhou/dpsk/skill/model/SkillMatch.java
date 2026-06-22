package com.xiaomizhou.dpsk.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 检索匹配结果。
 * <p>
 * 经过去重合并后的最终命中结果，包含 Skill 的完整描述。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMatch {

    /** Skill 唯一标识 */
    private String skillId;

    /** Skill 名称 */
    private String skillName;

    /** Skill 完整描述 */
    private String fullDescription;

    /** 检索分数（去重前各粒度中的最高分） */
    private double score;

    /** 业务元数据 */
    @Builder.Default
    private Map<String, String> metadata = new LinkedHashMap<>();

    /**
     * 转为注入 LLM 的文本格式。
     */
    public String toInjectText() {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(skillName).append(" (").append(skillId).append(")\n");
        sb.append(fullDescription).append("\n");
        return sb.toString();
    }
}
