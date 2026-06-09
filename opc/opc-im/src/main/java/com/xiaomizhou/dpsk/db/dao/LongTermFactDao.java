package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.LongTermFactMapper;
import com.xiaomizhou.dpsk.db.model.LongTermFactDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * L2 长期事实 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/29
 */
@Component
@Slf4j
public class LongTermFactDao extends ServiceImpl<LongTermFactMapper, LongTermFactDO> {

    /**
     * 根据 ownerCode 和 targetCode 查询所有未删除的事实，按重要性降序。
     */
    public List<LongTermFactDO> findByOwnerAndTarget(String ownerCode, String targetCode) {
        return lambdaQuery()
                .eq(LongTermFactDO::getOwnerCode, ownerCode)
                .eq(LongTermFactDO::getTargetCode, targetCode)
                .eq(LongTermFactDO::getIsDeleted, 0)
                .orderByDesc(LongTermFactDO::getImportance)
                .list();
    }

    /**
     * 保存事实。
     */
    public boolean save(LongTermFactDO entity) {
        return super.save(entity);
    }

    /**
     * 根据编码查询单条事实。
     */
    public LongTermFactDO getByCode(String code) {
        return lambdaQuery()
                .eq(LongTermFactDO::getCode, code)
                .eq(LongTermFactDO::getIsDeleted, 0)
                .one();
    }

    /**
     * 更新最后访问时间。
     */
    public boolean updateLastAccessedTime(String code) {
        return lambdaUpdate()
                .eq(LongTermFactDO::getCode, code)
                .set(LongTermFactDO::getLastAccessedTime, new Date())
                .set(LongTermFactDO::getUpdateTime, new Date())
                .update();
    }
}
