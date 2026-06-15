package com.xiaomizhou.dpsk.memory.repository;

import com.xiaomizhou.dpsk.memory.model.LongTermFact;

import java.util.List;

/**
 * L2 长期事实仓储接口。
 * <p>
 * 具体实现由 opc-im 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
public interface LongTermFactRepository {

    /**
     * 根据 ownerCode 和 targetCode 查询所有未删除的事实。
     *
     * @param ownerCode  Agent 编码
     * @param targetCode 目标编码（用户/群组）
     * @return 事实列表
     */
    List<LongTermFact> findByOwnerAndTarget(String ownerCode, String targetCode);

    /**
     * 根据编码查询单条事实。
     *
     * @param code 事实编码
     * @return 事实实体，无则返回 null
     */
    LongTermFact findByCode(String code);

    /**
     * 保存事实记录。
     *
     * @param fact 事实实体
     */
    void save(LongTermFact fact);

    /**
     * 更新最后一次访问时间。
     *
     * @param code 事实编码
     */
    void updateLastAccessedTime(String code);

    /**
     * 查询所有未删除的事实（用于恢复向量索引）。
     *
     * @return 所有未删除的事实列表
     */
    List<LongTermFact> findAllUnEmbedding();


    /**
     * 将事实标记为待向量化。
     * @param code
     * @return
     */
    boolean toBedEmbedding(String code);
}
