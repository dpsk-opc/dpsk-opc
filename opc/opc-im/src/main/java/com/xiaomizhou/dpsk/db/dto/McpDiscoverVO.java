package com.xiaomizhou.dpsk.db.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 工具发现响应。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpDiscoverVO {
    private boolean runtimeAvailable;
    private String runtimeEnv;
    private List<McpToolItem> tools;
}
