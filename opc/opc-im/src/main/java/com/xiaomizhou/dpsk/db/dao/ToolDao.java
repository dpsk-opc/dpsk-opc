package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.ToolMapper;
import com.xiaomizhou.dpsk.db.model.ToolDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 工具元数据 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Component
@Slf4j
public class ToolDao extends ServiceImpl<ToolMapper, ToolDO> {

    /**
     * 查询所有启用的工具。
     */
    public List<ToolDO> findAllEnabled() {
        return lambdaQuery()
                .eq(ToolDO::getStatus, "ENABLED")
                .eq(ToolDO::getIsDeleted, 0)
                .list();
    }

    /**
     * 根据名称查询。
     */
    public ToolDO getByName(String name) {
        return lambdaQuery()
                .eq(ToolDO::getName, name)
                .eq(ToolDO::getIsDeleted, 0)
                .one();
    }

    /**
     * 根据编码查询。
     */
    public ToolDO getByCode(String code) {
        return lambdaQuery()
                .eq(ToolDO::getCode, code)
                .eq(ToolDO::getIsDeleted, 0)
                .one();
    }

    /**
     * 根据来源类型查询启用的工具。
     */
    public List<ToolDO> findBySourceType(String sourceType) {
        return lambdaQuery()
                .eq(ToolDO::getSourceType, sourceType)
                .eq(ToolDO::getStatus, "ENABLED")
                .eq(ToolDO::getIsDeleted, 0)
                .list();
    }

    /**
     * 根据分类查询。
     */
    public List<ToolDO> findByCategory(String category) {
        return lambdaQuery()
                .eq(ToolDO::getCategory, category)
                .eq(ToolDO::getIsDeleted, 0)
                .list();
    }

//    /**
//     * 根据所属 Agent 查询启用的工具（包含公共工具）。
//     */
//    public List<ToolDO> findByOwnerAgent(String ownerAgentCode) {
//        return lambdaQuery()
//                .and(wrapper -> wrapper
//                        .eq(ToolDO::getOwnerAgentCode, ownerAgentCode)
//                        .or()
//                        .eq(ToolDO::getOwnerAgentCode, "")
//                )
//                .eq(ToolDO::getStatus, "ENABLED")
//                .eq(ToolDO::getIsDeleted, 0)
//                .list();
//    }

    /**
     * 关键词搜索工具（匹配 name、description、category、tags）。
     */
    public List<ToolDO> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAllEnabled();
        }
        return lambdaQuery()
                .and(wrapper -> wrapper
                        .like(ToolDO::getName, keyword)
                        .or()
                        .like(ToolDO::getDescription, keyword)
                        .or()
                        .like(ToolDO::getCategory, keyword)
                        .or()
                        .like(ToolDO::getTags, keyword)
                )
                .eq(ToolDO::getStatus, "ENABLED")
                .eq(ToolDO::getIsDeleted, 0)
                .list();
    }

    /**
     * 保存工具。
     */
    public boolean save(ToolDO entity) {
        return super.save(entity);
    }

    /**
     * 批量保存。
     */
    public boolean saveBatch(List<ToolDO> entities) {
        return super.saveBatch(entities);
    }

    /**
     * 根据 ID 更新。
     */
    public boolean updateById(ToolDO entity) {
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    /**
     * 根据编码更新状态。
     */
    public boolean updateStatus(String code, String status) {
        return lambdaUpdate()
                .eq(ToolDO::getCode, code)
                .set(ToolDO::getStatus, status)
                .set(ToolDO::getUpdateTime, new Date())
                .update();
    }

    /**
     * 根据编码逻辑删除。
     */
    public boolean deleteByCode(String code) {
        return lambdaUpdate()
                .eq(ToolDO::getCode, code)
                .set(ToolDO::getIsDeleted, 1)
                .set(ToolDO::getUpdateTime, new Date())
                .update();
    }

    /**
     * 检查编码是否存在。
     */
    public boolean existsByCode(String code) {
        return lambdaQuery()
                .eq(ToolDO::getCode, code)
                .eq(ToolDO::getIsDeleted, 0)
                .count() > 0;
    }
}
