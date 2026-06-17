package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;
import java.util.Map;

/**
 * MCP 工具项。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
public class McpToolItem {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
}
