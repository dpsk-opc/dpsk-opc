package com.xiaomizhou.dpsk.memory.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiaomizhou.dpsk.db.dao.LongTermFactDao;
import com.xiaomizhou.dpsk.db.model.LongTermFactDO;
import com.xiaomizhou.dpsk.memory.model.LongTermFact;
import com.xiaomizhou.dpsk.memory.repository.LongTermFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LongTermFactRepository 实现。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LongTermFactRepositoryImpl implements LongTermFactRepository {

    private final LongTermFactDao longTermFactDao;

    @Override
    public List<LongTermFact> findByOwnerAndTarget(String ownerCode, String targetCode) {
        return longTermFactDao.findByOwnerAndTarget(ownerCode, targetCode).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public LongTermFact findByCode(String code) {
        LongTermFactDO entity = longTermFactDao.getByCode(code);
        return toCoreModel(entity);
    }

    @Override
    public void save(LongTermFact fact) {
        LongTermFactDO entity = toDbModel(fact);
        longTermFactDao.save(entity);
        log.debug("Saved long-term fact: code={}, type={}", fact.getCode(), fact.getFactType());
    }

    @Override
    public void updateLastAccessedTime(String code) {
        longTermFactDao.updateLastAccessedTime(code);
    }

    @Override
    public List<LongTermFact> findAllUnEmbedding() {
        return longTermFactDao.findAllUnEmbedding().stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public boolean toBedEmbedding(String code) {
        longTermFactDao.update(Wrappers.<LongTermFactDO>lambdaUpdate().eq(LongTermFactDO::getCode, code)
                .set(LongTermFactDO::getStatus, 1));
        return true;
    }

    private LongTermFact toCoreModel(LongTermFactDO entity) {
        if (entity == null) {
            return null;
        }
        LongTermFact model = new LongTermFact();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setOwnerCode(entity.getOwnerCode());
        model.setTargetCode(entity.getTargetCode());
        model.setFactType(entity.getFactType());
        model.setFactContent(entity.getFactContent());
        model.setImportance(entity.getImportance());
        model.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime().toInstant() : null);
        model.setLastAccessedTime(entity.getLastAccessedTime() != null ? entity.getLastAccessedTime().toInstant() : null);
        model.setIsDeleted(entity.getIsDeleted());
        return model;
    }

    private LongTermFactDO toDbModel(LongTermFact model) {
        LongTermFactDO entity = new LongTermFactDO();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setOwnerCode(model.getOwnerCode());
        entity.setTargetCode(model.getTargetCode());
        entity.setFactType(model.getFactType());
        entity.setFactContent(model.getFactContent());
        entity.setImportance(model.getImportance());
        entity.setLastAccessedTime(model.getLastAccessedTime() != null
                ? java.util.Date.from(model.getLastAccessedTime()) : new java.util.Date());
        entity.setIsDeleted(model.getIsDeleted() != null ? model.getIsDeleted() : 0);
        entity.setUpdateTime(new Date());
        entity.setCreateTime(new Date());
        return entity;
    }
}
