package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.McpTemplateMapper;
import com.xiaomizhou.dpsk.db.model.McpTemplateDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 服务模板 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Component
@Slf4j
public class McpTemplateDao extends ServiceImpl<McpTemplateMapper, McpTemplateDO> {

    public McpTemplateDO getByCode(String code) {
        return lambdaQuery()
                .eq(McpTemplateDO::getCode, code)
                .eq(McpTemplateDO::getIsDeleted, 0)
                .one();
    }

    public List<McpTemplateDO> findAll() {
        return lambdaQuery()
                .eq(McpTemplateDO::getIsDeleted, 0)
                .list();
    }

    public List<McpTemplateDO> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        return lambdaQuery()
                .and(wrapper -> wrapper
                        .like(McpTemplateDO::getName, keyword)
                        .or()
                        .like(McpTemplateDO::getDescription, keyword)
                        .or()
                        .like(McpTemplateDO::getCommand, keyword)
                )
                .eq(McpTemplateDO::getIsDeleted, 0)
                .list();
    }

    public boolean deleteByCode(String code) {
        return lambdaUpdate()
                .eq(McpTemplateDO::getCode, code)
                .set(McpTemplateDO::getIsDeleted, 1)
                .update();
    }
}
