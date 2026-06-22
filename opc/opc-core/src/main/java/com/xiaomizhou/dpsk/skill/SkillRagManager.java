package com.xiaomizhou.dpsk.skill;

import com.xiaomizhou.dpsk.rag.RagNamespace;
import com.xiaomizhou.dpsk.rag.RagService;
import com.xiaomizhou.dpsk.rag.model.Document;
import com.xiaomizhou.dpsk.rag.model.RagFilter;
import com.xiaomizhou.dpsk.rag.model.RagHit;
import com.xiaomizhou.dpsk.skill.model.SkillDoc;
import com.xiaomizhou.dpsk.skill.model.SkillMatch;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill RAG 管理器。
 * <p>
 * 负责 Skill 的 RAG 索引写入和多粒度检索。
 * <p>
 * <b>核心策略：RAG 做"海选"（多召回），LLM 做"决赛"（精判断）</b>
 * <ul>
 *   <li>写入：通过 {@link SkillIndexBuilder} 将 Skill 拆分为多粒度索引文档</li>
 *   <li>检索：多粒度检索 → 按 source_id 去重 → 返回 Top-K 完整描述</li>
 *   <li>权限：通过 {@link RagFilter} 的 in/equals 条件过滤可见 Skill</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * // 1. 索引 Skill
 * SkillDoc skill = SkillDoc.builder()
 *     .skillId("create_scheduled_task")
 *     .skillName("创建定时任务")
 *     .fullDescription("这是一个用于创建定时任务的技能...")
 *     .searchTexts(List.of("创建定时任务", "设置定时消息推送"))
 *     .keywords(List.of("定时", "cron"))
 *     .triggerQuestions(List.of("怎么设置每天8点发消息？"))
 *     .metadata(Map.of("status", "enabled", "owner_code", "agent_a"))
 *     .build();
 * skillRagManager.indexSkill(skill);
 *
 * // 2. 检索 Skill
 * List<SkillMatch> matches = skillRagManager.searchSkills(
 *     "怎么设置每天8点发消息", "agent_a", 5);
 *
 * // 3. 注入到 LLM prompt
 * for (SkillMatch match : matches) {
 *     prompt.append(match.toInjectText());
 * }
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/21
 */
@Slf4j
public class SkillRagManager {

    private final RagService ragService;

    /** 默认最低相似度阈值 */
    private static final double DEFAULT_MIN_SCORE = 0.55;

    /** 检索时的放大系数（多取一些，因为要去重） */
    private static final int FETCH_MULTIPLIER = 3;

    public SkillRagManager(RagService ragService) {
        this.ragService = Objects.requireNonNull(ragService, "ragService must not be null");
    }

    // ==================== 写入 ====================

    /**
     * 索引单个 Skill。
     * <p>
     * 先删除旧索引（按 source_id），再写入新索引。
     *
     * @param skill Skill 文档
     * @return 写入的文档数量
     */
    public int indexSkill(SkillDoc skill) {
        // 先删除旧索引
        deleteBySkillId(skill.getSkillId());

        List<Document> docs = SkillIndexBuilder.build(skill);
        if (docs.isEmpty()) {
            log.warn("No documents generated for skill: {}", skill.getSkillId());
            return 0;
        }

        ragService.addDocuments(RagNamespace.SKILL, docs);
        log.info("Indexed skill '{}' ({}): {} documents", skill.getSkillId(), skill.getSkillName(), docs.size());
        return docs.size();
    }

    /**
     * 批量索引多个 Skill。
     *
     * @param skills Skill 文档列表
     * @return 总写入文档数
     */
    public int indexSkills(List<SkillDoc> skills) {
        if (skills == null || skills.isEmpty()) return 0;

        int total = 0;
        for (SkillDoc skill : skills) {
            total += indexSkill(skill);
        }
        return total;
    }

    // ==================== 检索 ====================

    /**
     * 检索相关 Skill（无过滤条件）。
     *
     * @param query  用户查询文本
     * @param topK   返回最大 Skill 数量
     * @return 去重后的 Skill 匹配结果（按分数降序）
     */
    public List<SkillMatch> searchSkills(String query, int topK) {
        return searchSkills(query, topK, DEFAULT_MIN_SCORE, null);
    }

    /**
     * 带过滤条件的 Skill 检索。
     * <p>
     * 常用过滤：
     * <ul>
     *   <li>状态：{@code filter.equals("status", "enabled")}</li>
     *   <li>归属：{@code filter.equals("owner_code", "agent_a")}</li>
     *   <li>权限：{@code filter.in("visible_to", Set.of("user_001", "role_admin"))}</li>
     * </ul>
     *
     * @param query    用户查询文本
     * @param topK     返回最大 Skill 数量
     * @param minScore 最低相似度阈值
     * @param filter   过滤条件（null 表示不过滤）
     * @return 去重后的 Skill 匹配结果（按分数降序）
     */
    public List<SkillMatch> searchSkills(String query, int topK, double minScore, RagFilter filter) {
        if (query == null || query.isBlank()) return List.of();

        // 多取一些，因为多粒度索引会产生重复 source_id
        List<RagHit> hits = ragService.search(RagNamespace.SKILL, query, topK * FETCH_MULTIPLIER, minScore, filter);

        if (hits.isEmpty()) {
            log.debug("No skill matches for query: {}", query);
            return List.of();
        }

        return deduplicateAndRank(hits, topK);
    }

    /**
     * 检索 Skill 并格式化为可直接注入 LLM 的文本。
     * <p>
     * 适合直接拼接到 system prompt 或 context 中。
     *
     * @param query    用户查询
     * @param topK     返回最大数量
     * @param minScore 最低分数
     * @param filter   过滤条件
     * @return 格式化文本（空字符串表示无匹配）
     */
    public String searchAndFormat(String query, int topK, double minScore, RagFilter filter) {
        List<SkillMatch> matches = searchSkills(query, topK, minScore, filter);
        if (matches.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【相关可用技能】\n");
        for (int i = 0; i < matches.size(); i++) {
            sb.append(matches.get(i).toInjectText());
            if (i < matches.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    // ==================== 删除 ====================

    /**
     * 删除指定 Skill 的所有索引文档。
     *
     * @param skillId Skill 唯一标识
     */
    public void deleteBySkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) return;

        int deleted = ragService.deleteByFilter(RagNamespace.SKILL,
                Map.of(SkillIndexBuilder.META_SOURCE_ID, skillId));
        if (deleted > 0) {
            log.info("Deleted {} index documents for skill: {}", deleted, skillId);
        }
    }

    /**
     * 清空所有 Skill 索引。
     */
    public void clearAll() {
        ragService.clearNamespace(RagNamespace.SKILL);
        log.info("Cleared all skill indexes");
    }

    // ==================== 统计 ====================

    /**
     * 获取已索引的 Skill 数量（按 source_id 去重）。
     */
    public long countSkills() {
        // 由于多粒度索引，需要查所有文档后按 source_id 去重
        // 这是一个近似统计，精度取决于 count 实现
        return ragService.count(RagNamespace.SKILL);
    }

    // ==================== 内部方法 ====================

    /**
     * 按 source_id 去重并排序。
     * <p>
     * 多粒度索引会产生多个文档指向同一个 source_id（如 searchText + keywords + triggerQuestions），
     * 去重时取最高分。
     */
    private List<SkillMatch> deduplicateAndRank(List<RagHit> hits, int topK) {
        Map<String, SkillMatch> deduped = new LinkedHashMap<>();

        for (RagHit hit : hits) {
            String sourceId = hit.getMeta(SkillIndexBuilder.META_SOURCE_ID);
            if (sourceId == null) continue;

            deduped.compute(sourceId, (k, existing) -> {
                if (existing == null || hit.getScore() > existing.getScore()) {
                    return SkillMatch.builder()
                            .skillId(sourceId)
                            .skillName(hit.getMeta(SkillIndexBuilder.META_SKILL_NAME))
                            .fullDescription(hit.getMeta(SkillIndexBuilder.META_FULL_DESCRIPTION))
                            .score(hit.getScore())
                            .metadata(extractBusinessMeta(hit.getMetadata()))
                            .build();
                }
                return existing;
            });
        }

        return deduped.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 从 metadata 中提取业务字段（排除内部字段）。
     */
    private Map<String, String> extractBusinessMeta(Map<String, String> rawMeta) {
        if (rawMeta == null) return new LinkedHashMap<>();
        Map<String, String> biz = new LinkedHashMap<>(rawMeta);
        // 移除内部索引字段
        biz.remove(SkillIndexBuilder.META_SOURCE_ID);
        biz.remove(SkillIndexBuilder.META_GRAIN);
        biz.remove(SkillIndexBuilder.META_SKILL_NAME);
        biz.remove(SkillIndexBuilder.META_FULL_DESCRIPTION);
        return biz;
    }
}
