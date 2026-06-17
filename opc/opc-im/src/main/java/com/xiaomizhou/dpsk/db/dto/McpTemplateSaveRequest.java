package com.xiaomizhou.dpsk.db.dto;

import lombok.Data;
import java.util.List;

/**
 * MCP 模板新增/更新请求。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
public class McpTemplateSaveRequest {
    private String id;
    private String name;
    private String description;
    private String command;
    private String[] args;
    private Boolean runtimeAvailable;
    private String runtimeEnv;
    private List<McpToolItem> tools;
}
