package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 工具调用审计日志模型，对应 t_tool_audit_log 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolAuditLog {

    /** 主键 */
    private Long id;

    /** 审计编码 */
    private String code;

    /** 工具编码 */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** Agent 编码 */
    private String agentCode;

    /** 用户编码 */
    private String userCode;

    /** 会话编码 */
    private String conversationCode;

    /** 调用参数（脱敏后的JSON） */
    private String requestParams;

    /** 结果摘要 */
    private String responseSummary;

    /** 执行状态：SUCCESS, FAIL, PENDING, CANCELLED */
    private String status;

    /** 风险等级 */
    private String riskLevel;

    /** 执行耗时（毫秒） */
    private Integer executionTimeMs;

    /** 错误信息 */
    private String errorMessage;

    /** 链路追踪ID */
    private String traceId;

    /** 创建时间 */
    private Instant createTime;

    // ---- 常量 ----
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
