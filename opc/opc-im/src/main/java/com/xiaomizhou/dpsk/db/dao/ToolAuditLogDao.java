package com.xiaomizhou.dpsk.db.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaomizhou.dpsk.db.mapper.ToolAuditLogMapper;
import com.xiaomizhou.dpsk.db.model.ToolAuditLogDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 工具审计日志 DAO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Component
@Slf4j
public class ToolAuditLogDao extends ServiceImpl<ToolAuditLogMapper, ToolAuditLogDO> {

    /**
     * 保存审计日志。
     */
    public boolean save(ToolAuditLogDO entity) {
        if (Objects.isNull(entity.getCreateTime())) {
            entity.setCreateTime(new Date());
        }

        if (Objects.isNull(entity.getUpdateTime())) {
            entity.setUpdateTime(new Date());
        }


        return super.save(entity);
    }

    /**
     * 根据工具编码分页查询。
     */
    public List<ToolAuditLogDO> findByToolCode(String toolCode, int limit) {
        return lambdaQuery()
                .eq(ToolAuditLogDO::getToolCode, toolCode)
                .eq(ToolAuditLogDO::getIsDeleted, 0)
                .orderByDesc(ToolAuditLogDO::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 根据 Agent 编码分页查询。
     */
    public List<ToolAuditLogDO> findByAgentCode(String agentCode, int limit) {
        return lambdaQuery()
                .eq(ToolAuditLogDO::getAgentCode, agentCode)
                .eq(ToolAuditLogDO::getIsDeleted, 0)
                .orderByDesc(ToolAuditLogDO::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 根据用户编码分页查询。
     */
    public List<ToolAuditLogDO> findByUserCode(String userCode, int limit) {
        return lambdaQuery()
                .eq(ToolAuditLogDO::getUserCode, userCode)
                .eq(ToolAuditLogDO::getIsDeleted, 0)
                .orderByDesc(ToolAuditLogDO::getCreateTime)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 根据 TraceId 查询。
     */
    public List<ToolAuditLogDO> findByTraceId(String traceId) {
        return lambdaQuery()
                .eq(ToolAuditLogDO::getTraceId, traceId)
                .eq(ToolAuditLogDO::getIsDeleted, 0)
                .orderByDesc(ToolAuditLogDO::getCreateTime)
                .list();
    }
}
