package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.db.dao.ToolAuditLogDao;
import com.xiaomizhou.dpsk.db.model.ToolAuditLogDO;
import com.xiaomizhou.dpsk.tool.model.ToolAuditLog;
import com.xiaomizhou.dpsk.tool.repository.ToolAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ToolAuditLogRepository 实现，基于 MyBatis-Plus。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ToolAuditLogRepositoryImpl implements ToolAuditLogRepository {

    private final ToolAuditLogDao toolAuditLogDao;

    @Override
    public void save(ToolAuditLog auditLog) {
        ToolAuditLogDO entity = toDbModel(auditLog);
        toolAuditLogDao.save(entity);
    }

    @Override
    public List<ToolAuditLog> findByToolCode(String toolCode, int limit) {
        return toolAuditLogDao.findByToolCode(toolCode, limit).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolAuditLog> findByAgentCode(String agentCode, int limit) {
        return toolAuditLogDao.findByAgentCode(agentCode, limit).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolAuditLog> findByUserCode(String userCode, int limit) {
        return toolAuditLogDao.findByUserCode(userCode, limit).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolAuditLog> findByTraceId(String traceId) {
        return toolAuditLogDao.findByTraceId(traceId).stream()
                .map(this::toCoreModel)
                .collect(Collectors.toList());
    }

    // ---- 模型转换 ----

    private ToolAuditLog toCoreModel(ToolAuditLogDO entity) {
        if (entity == null) {
            return null;
        }
        return ToolAuditLog.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .toolCode(entity.getToolCode())
                .toolName(entity.getToolName())
                .agentCode(entity.getAgentCode())
                .userCode(entity.getUserCode())
                .conversationCode(entity.getConversationCode())
                .requestParams(entity.getRequestParams())
                .responseSummary(entity.getResponseSummary())
                .status(entity.getStatus())
                .riskLevel(entity.getRiskLevel())
                .executionTimeMs(entity.getExecutionTimeMs())
                .errorMessage(entity.getErrorMessage())
                .traceId(entity.getTraceId())
                .createTime(entity.getCreateTime() != null ? entity.getCreateTime().toInstant() : null)
                .build();
    }

    private ToolAuditLogDO toDbModel(ToolAuditLog model) {
        ToolAuditLogDO entity = new ToolAuditLogDO();
        entity.setCode(model.getCode());
        entity.setToolCode(model.getToolCode());
        entity.setToolName(model.getToolName());
        entity.setAgentCode(model.getAgentCode());
        entity.setUserCode(model.getUserCode());
        entity.setConversationCode(model.getConversationCode());
        entity.setRequestParams(model.getRequestParams());
        entity.setResponseSummary(StringUtils.left(model.getResponseSummary(), 500));
        entity.setStatus(model.getStatus());
        entity.setRiskLevel(model.getRiskLevel());
        entity.setExecutionTimeMs(model.getExecutionTimeMs());
        entity.setErrorMessage(model.getErrorMessage());
        entity.setTraceId(model.getTraceId());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        entity.setIsDeleted(0);
        return entity;
    }
}
