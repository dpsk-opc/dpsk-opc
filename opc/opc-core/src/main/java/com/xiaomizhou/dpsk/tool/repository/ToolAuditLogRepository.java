package com.xiaomizhou.dpsk.tool.repository;

import com.xiaomizhou.dpsk.tool.model.ToolAuditLog;

import java.util.List;

/**
 * 工具调用审计日志仓储接口。
 * <p>
 * opc-core 仅定义接口，具体实现由 opc-im 基于 MyBatis-Plus 提供。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
public interface ToolAuditLogRepository {

    /**
     * 保存审计日志。
     *
     * @param auditLog 审计日志
     */
    void save(ToolAuditLog auditLog);

    /**
     * 根据工具编码分页查询审计日志。
     *
     * @param toolCode 工具编码
     * @param limit    最大条数
     * @return 审计日志列表（按时间降序）
     */
    List<ToolAuditLog> findByToolCode(String toolCode, int limit);

    /**
     * 根据 Agent 编码分页查询审计日志。
     *
     * @param agentCode Agent 编码
     * @param limit     最大条数
     * @return 审计日志列表（按时间降序）
     */
    List<ToolAuditLog> findByAgentCode(String agentCode, int limit);

    /**
     * 根据用户编码分页查询审计日志。
     *
     * @param userCode 用户编码
     * @param limit    最大条数
     * @return 审计日志列表（按时间降序）
     */
    List<ToolAuditLog> findByUserCode(String userCode, int limit);

    /**
     * 根据 TraceId 查询审计日志。
     *
     * @param traceId 链路追踪ID
     * @return 审计日志列表
     */
    List<ToolAuditLog> findByTraceId(String traceId);
}
