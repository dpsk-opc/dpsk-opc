package com.xiaomizhou.dpsk.memory.impl;

import com.xiaomizhou.dpsk.db.dao.MemorySummaryDao;
import com.xiaomizhou.dpsk.db.model.MemorySummaryDO;
import com.xiaomizhou.dpsk.memory.model.MemorySummary;
import com.xiaomizhou.dpsk.memory.repository.MemorySummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MemorySummaryRepository 实现。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MemorySummaryRepositoryImpl implements MemorySummaryRepository {

    private final MemorySummaryDao memorySummaryDao;

    @Override
    public MemorySummary findLatest(String conversationCode, String ownerCode) {
        MemorySummaryDO entity = memorySummaryDao.findLatest(conversationCode, ownerCode);
        return toCoreModel(entity);
    }

    @Override
    public void save(MemorySummary summary) {
        MemorySummaryDO entity = toDbModel(summary);
        memorySummaryDao.save(entity);
        log.debug("Saved memory summary: code={}, owner={}", summary.getCode(), summary.getOwnerCode());
    }

    @Override
    public boolean existsByRange(String conversationCode, String ownerCode, String startMessageCode) {
        return memorySummaryDao.existsByRange(conversationCode, ownerCode, startMessageCode);
    }

    private MemorySummary toCoreModel(MemorySummaryDO entity) {
        if (entity == null) {
            return null;
        }
        MemorySummary model = new MemorySummary();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setOwnerCode(entity.getOwnerCode());
        model.setConversationCode(entity.getConversationCode());
        model.setSummaryText(entity.getSummaryText());
        model.setStartMessageCode(entity.getStartMessageCode());
        model.setEndMessageCode(entity.getEndMessageCode());
        model.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime().toInstant() : null);
        model.setIsDeleted(entity.getIsDeleted());
        return model;
    }

    private MemorySummaryDO toDbModel(MemorySummary model) {
        MemorySummaryDO entity = new MemorySummaryDO();
        entity.setId(model.getId());
        entity.setCode(model.getCode());
        entity.setOwnerCode(model.getOwnerCode());
        entity.setConversationCode(model.getConversationCode());
        entity.setSummaryText(model.getSummaryText());
        entity.setStartMessageCode(model.getStartMessageCode());
        entity.setEndMessageCode(model.getEndMessageCode());
        entity.setIsDeleted(model.getIsDeleted() != null ? model.getIsDeleted() : 0);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        return entity;
    }
}
