package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.KnowledgeLibMapper;
import com.xiaomizhou.dpsk.db.model.KnowledgeLib;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库 DAO
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/13
 */
@Component
@Slf4j
public class KnowledgeLibDao extends ServiceImpl<KnowledgeLibMapper, KnowledgeLib> {

    /**
     * 根据编码查询知识库
     */
    public KnowledgeLib getByCode(String code) {
        return getOne(Wrappers.<KnowledgeLib>lambdaQuery().eq(KnowledgeLib::getCode, code));
    }

    /**
     * 根据归属实体查询知识库列表
     */
    public List<KnowledgeLib> listByOwner(String ownerCode, Integer ownerType) {
        return list(Wrappers.<KnowledgeLib>lambdaQuery()
                .eq(KnowledgeLib::getOwnerCode, ownerCode)
                .eq(KnowledgeLib::getOwnerType, ownerType));
    }

    /**
     * CAS 更新知识库状态
     *
     * @param libCode        知识库编码
     * @param expectedStatus 期望的当前状态
     * @param newStatus      目标新状态
     * @return 是否更新成功
     */
    public boolean casUpdateStatus(String libCode, int expectedStatus, int newStatus) {
        return update(Wrappers.<KnowledgeLib>lambdaUpdate()
                .set(KnowledgeLib::getStatus, newStatus)
                .set(KnowledgeLib::getUpdateTime, new java.util.Date())
                .eq(KnowledgeLib::getCode, libCode)
                .eq(KnowledgeLib::getStatus, expectedStatus));
    }

}
