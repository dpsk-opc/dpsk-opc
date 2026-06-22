package com.xiaomizhou.dpsk.skill;

import com.xiaomizhou.dpsk.rag.model.Document;
import com.xiaomizhou.dpsk.skill.model.SkillDoc;

import java.util.*;

/**
 * Skill 多粒度 RAG 索引构建器。
 * <p>
 * 将一条 {@link SkillDoc} 拆分为多条 RAG 索引文档：
 * <ul>
 *   <li>每条 searchText → 一条独立文档（grain=search_text）</li>
 *   <li>keywords 拼接 → 一条文档（grain=keyword）</li>
 *   <li>每条 triggerQuestion → 一条独立文档（grain=trigger_question）</li>
 * </ul>
 * <p>
 * 所有文档共享相同的 source_id，检索后可按 source_id 去重合并。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * SkillDoc skill = SkillDoc.builder()
 *     .skillId("create_scheduled_task")
 *     .skillName("创建定时任务")
 *     .fullDescription("这是一个用于创建定时任务的技能...")
 *     .searchTexts(List.of("创建定时任务", "设置定时消息推送", "定时执行HTTP请求"))
 *     .keywords(List.of("定时", "cron", "调度"))
 *     .triggerQuestions(List.of("怎么设置每天8点发消息？", "如何创建定时备份任务？"))
 *     .build();
 *
 * List<Document> docs = SkillIndexBuilder.build(skill);
 * ragService.addDocuments(RagNamespace.SKILL, docs);
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
public class SkillIndexBuilder {

    /** metadata key: 源文档 ID，用于检索后去重 */
    public static final String META_SOURCE_ID = "source_id";
    /** metadata key: 索引粒度 */
    public static final String META_GRAIN = "grain";
    /** metadata key: Skill 名称 */
    public static final String META_SKILL_NAME = "skill_name";
    /** metadata key: 完整描述（payload） */
    public static final String META_FULL_DESCRIPTION = "full_description";

    /** 粒度值 */
    public static final String GRAIN_SEARCH_TEXT = "search_text";
    public static final String GRAIN_KEYWORD = "keyword";
    public static final String GRAIN_TRIGGER_QUESTION = "trigger_question";

    private SkillIndexBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 将 SkillDoc 拆分为多条 RAG 索引文档。
     *
     * @param skill Skill 文档
     * @return RAG 索引文档列表
     */
    public static List<Document> build(SkillDoc skill) {
        if (skill == null || skill.getSkillId() == null) {
            return List.of();
        }

        List<Document> docs = new ArrayList<>();

        // 构建公共 metadata
        Map<String, String> baseMeta = new LinkedHashMap<>();
        baseMeta.put(META_SOURCE_ID, skill.getSkillId());
        baseMeta.put(META_SKILL_NAME, skill.getSkillName() != null ? skill.getSkillName() : "");
        baseMeta.put(META_FULL_DESCRIPTION, skill.getFullDescription() != null ? skill.getFullDescription() : "");
        // 合并业务 metadata
        if (skill.getMetadata() != null) {
            baseMeta.putAll(skill.getMetadata());
        }

        // 1. searchTexts 索引（每条独立）
        if (skill.getSearchTexts() != null) {
            for (int i = 0; i < skill.getSearchTexts().size(); i++) {
                String text = skill.getSearchTexts().get(i);
                if (text == null || text.isBlank()) continue;

                Map<String, String> meta = new LinkedHashMap<>(baseMeta);
                meta.put(META_GRAIN, GRAIN_SEARCH_TEXT);

                String docId = skill.getSkillId() + "_st_" + i;
                docs.add(Document.of(docId, text, meta));
            }
        }

        // 2. keywords 索引（拼接为一条）
        if (skill.getKeywords() != null && !skill.getKeywords().isEmpty()) {
            String keywordText = String.join(" ", skill.getKeywords());
            Map<String, String> meta = new LinkedHashMap<>(baseMeta);
            meta.put(META_GRAIN, GRAIN_KEYWORD);

            String docId = skill.getSkillId() + "_kw";
            docs.add(Document.of(docId, keywordText, meta));
        }

        // 3. triggerQuestions 索引（每条独立）
        if (skill.getTriggerQuestions() != null) {
            for (int i = 0; i < skill.getTriggerQuestions().size(); i++) {
                String question = skill.getTriggerQuestions().get(i);
                if (question == null || question.isBlank()) continue;

                Map<String, String> meta = new LinkedHashMap<>(baseMeta);
                meta.put(META_GRAIN, GRAIN_TRIGGER_QUESTION);

                String docId = skill.getSkillId() + "_tq_" + i;
                docs.add(Document.of(docId, question, meta));
            }
        }

        return docs;
    }

    /**
     * 批量构建多个 SkillDoc 的索引文档。
     */
    public static List<Document> buildAll(List<SkillDoc> skills) {
        if (skills == null || skills.isEmpty()) return List.of();
        List<Document> allDocs = new ArrayList<>();
        for (SkillDoc skill : skills) {
            allDocs.addAll(build(skill));
        }
        return allDocs;
    }

    /**
     * 从命中列表中提取 source_id（用于去重）。
     */
    public static String getSourceId(Document doc) {
        return doc.getMetadata() != null ? doc.getMetadata().get(META_SOURCE_ID) : null;
    }
}
