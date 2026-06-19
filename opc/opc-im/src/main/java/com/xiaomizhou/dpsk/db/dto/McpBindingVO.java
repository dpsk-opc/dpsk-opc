package com.xiaomizhou.dpsk.db.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 绑定 VO。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpBindingVO {
    private String code;
    private String templateId;
    private String templateName;
    private String command;
    private String[] args;
    private String agentCode;
    private boolean enabled;
    private String envVars;
    private String status;
    private boolean runtimeAvailable;
    private String runtimeEnv;
    private List<McpToolItem> tools;
    private String createdAt;
    private String updatedAt;
}
