package com.xiaomizhou.dpsk.db.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 模板 VO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpTemplateVO {
    private String id;
    private String name;
    private String description;
    private String command;
    private String[] args;
    private boolean runtimeAvailable;
    private String runtimeEnv;
    private List<McpToolItem> tools;
    private String createdAt;
    private String updatedAt;
}
