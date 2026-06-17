package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP Electron 调用请求。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpElectronRequest {

    /** 调用 ID，用于匹配响应 */
    private String callId;

    /** 启动命令 */
    private String command;

    /** 命令参数 */
    private String[] args;

    /** 环境变量 */
    private Map<String, String> envVars;

    /** 工具名称 */
    private String toolName;

    /** 工具参数 JSON */
    private String arguments;
}
