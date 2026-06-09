package com.xiaomizhou.dpsk.memory.manager;

import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.memory.generator.SummaryGenerator;
import com.xiaomizhou.dpsk.memory.model.MemorySummary;
import com.xiaomizhou.dpsk.memory.repository.MemorySummaryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * L1 摘要记忆管理器。
 * <p>
 * 负责将被移出 L0 窗口的消息生成增量摘要，并持久化到 t_memory_summary 表。
 * 包含幂等性保护，避免重复生成。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public class SummaryManager {

    private final MemorySummaryRepository summaryRepository;
    private final SummaryGenerator summaryGenerator;

    public SummaryManager(MemorySummaryRepository summaryRepository, SummaryGenerator summaryGenerator) {
        this.summaryRepository = Objects.requireNonNull(summaryRepository, "summaryRepository must not be null");
        this.summaryGenerator = Objects.requireNonNull(summaryGenerator, "summaryGenerator must not be null");
    }

    /**
     * 更新摘要（由 MemoryManager 异步调用）。
     *
     * @param conversationCode 会话编码（群聊时为复合编码）
     * @param ownerCode        Agent 编码
     * @param agentName        Agent 名称
     * @param evicted          被移出窗口的消息文本列表
     */
    public void updateSummary(String conversationCode, String ownerCode,
                              String agentName, List<String> evicted) {
        if (evicted == null || evicted.size() < MemoryConfig.L1_MIN_EVICTED) {
            return;
        }

        // 幂等性检查：如果该范围已有摘要，跳过
        String firstCode = extractFirstCode(evicted);
        if (firstCode != null && summaryRepository.existsByRange(
                conversationCode, ownerCode, firstCode)) {
            return;
        }

        try {
            // 获取旧摘要
            MemorySummary lastSummary = summaryRepository.findLatest(
                    conversationCode, ownerCode);
            String previousSummary = (lastSummary != null) ? lastSummary.getSummaryText() : "无";

            // 生成新摘要
            String newSummary = summaryGenerator.generateIncrementalSummary(
                    agentName, previousSummary, evicted);

            // 持久化
            MemorySummary entity = new MemorySummary();
            entity.setCode("summary_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setOwnerCode(ownerCode);
            entity.setConversationCode(conversationCode);
            entity.setSummaryText(newSummary);
            entity.setStartMessageCode(firstCode);
            entity.setEndMessageCode(extractLastCode(evicted));
            entity.setCreateTime(Instant.now());
            entity.setIsDeleted(0);

            summaryRepository.save(entity);
        } catch (Exception e) {
            // 摘要生成失败不阻塞主流程
            System.err.println("[SummaryManager] Failed to update summary: " + e.getMessage());
        }
    }

    /**
     * 获取指定会话的最新摘要文本。
     *
     * @param conversationCode 会话编码
     * @param ownerCode        Agent 编码
     * @return 摘要文本，无则返回 null
     */
    public String getLatestSummary(String conversationCode, String ownerCode) {
        MemorySummary summary = summaryRepository.findLatest(conversationCode, ownerCode);
        return summary != null ? summary.getSummaryText() : null;
    }

    private String extractFirstCode(List<String> evicted) {
        // evicted 消息文本格式由 opc-im 约定，这里取第一条作为起点标记
        return evicted.isEmpty() ? null : "msg_" + evicted.get(0).hashCode();
    }

    private String extractLastCode(List<String> evicted) {
        return evicted.isEmpty() ? null : "msg_" + evicted.get(evicted.size() - 1).hashCode();
    }
}
