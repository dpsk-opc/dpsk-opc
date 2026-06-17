package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * MCP 绑定保存请求。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
public class McpBindingSaveRequest {
    private String templateId;
    private Boolean enabled;
    private String envVars;
    /** 覆盖 Header 中的 Agent-Code，用于绑定到非当前 Agent */
    private String agentCode;
}
