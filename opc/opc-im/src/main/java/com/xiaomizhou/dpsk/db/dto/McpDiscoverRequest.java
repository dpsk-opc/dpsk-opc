package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;

/**
 * MCP 工具发现请求。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
public class McpDiscoverRequest {
    private String command;
    private String[] args;
    private String envVars;
}
