package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * MCP 删除请求（模板/绑定通用）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
public class McpDeleteRequest {
    private String id;
    private String templateId;

    private String agentCode;
}
