package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.MemorySummaryMapper;
import com.xiaomizhou.dpsk.db.model.MemorySummaryDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * L1 摘要记忆 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
public class MemorySummaryDao extends ServiceImpl<MemorySummaryMapper, MemorySummaryDO> {

    /**
     * 查询指定会话中某个 Agent 的最新摘要（未删除）。
     */
    public MemorySummaryDO findLatest(String conversationCode, String ownerCode) {
        return lambdaQuery()
                .eq(MemorySummaryDO::getConversationCode, conversationCode)
                .eq(MemorySummaryDO::getOwnerCode, ownerCode)
                .eq(MemorySummaryDO::getIsDeleted, 0)
                .orderByDesc(MemorySummaryDO::getCreateTime)
                .last("LIMIT 1")
                .one();
    }

    /**
     * 检查指定消息范围内是否已存在摘要（幂等性判断）。
     */
    public boolean existsByRange(String conversationCode, String ownerCode, String startMessageCode) {
        return lambdaQuery()
                .eq(MemorySummaryDO::getConversationCode, conversationCode)
                .eq(MemorySummaryDO::getOwnerCode, ownerCode)
                .eq(MemorySummaryDO::getStartMessageCode, startMessageCode)
                .eq(MemorySummaryDO::getIsDeleted, 0)
                .count() > 0;
    }

    /**
     * 保存摘要。
     */
    public boolean save(MemorySummaryDO entity) {
        return super.save(entity);
    }
}
