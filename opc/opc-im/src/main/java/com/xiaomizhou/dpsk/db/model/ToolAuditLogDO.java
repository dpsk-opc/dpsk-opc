package com.xiaomizhou.dpsk.db.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaomizhou.dpsk.db.model.base.BaseModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具调用审计日志 DB 模型，对应 t_tool_audit_log 表。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@EqualsAndHashCode(callSuper = true)
@TableName("t_tool_audit_log")
@Data
public class ToolAuditLogDO extends BaseModel {

    @TableField("code")
    private String code;

    @TableField("tool_code")
    private String toolCode;

    @TableField("tool_name")
    private String toolName;

    @TableField("agent_code")
    private String agentCode;

    @TableField("user_code")
    private String userCode;

    @TableField("conversation_code")
    private String conversationCode;

    @TableField("request_params")
    private String requestParams;

    @TableField("response_summary")
    private String responseSummary;

    @TableField("status")
    private String status;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("execution_time_ms")
    private Integer executionTimeMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("trace_id")
    private String traceId;
}
