package com.xiaomizhou.dpsk.memory.repository;

import com.xiaomizhou.dpsk.memory.model.MemorySummary;

/**
 * L1 摘要记忆仓储接口。
 * <p>
 * 具体实现由 opc-im 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public interface MemorySummaryRepository {

    /**
     * 查询指定会话中某个 Agent 的最新摘要。
     *
     * @param conversationCode 会话编码
     * @param ownerCode        Agent 编码
     * @return 最新摘要，无则返回 null
     */
    MemorySummary findLatest(String conversationCode, String ownerCode);

    /**
     * 保存摘要记录。
     *
     * @param summary 摘要实体
     */
    void save(MemorySummary summary);

    /**
     * 检查指定消息范围内是否已存在摘要（幂等性判断）。
     *
     * @param conversationCode  会话编码
     * @param ownerCode         Agent 编码
     * @param startMessageCode  起始消息编码
     * @return 存在返回 true
     */
    boolean existsByRange(String conversationCode, String ownerCode, String startMessageCode);
}
