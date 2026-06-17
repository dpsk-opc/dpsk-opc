package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 工具调用 payload（后端 -> 前端 Electron）。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpCallPayload {

    @JsonProperty("callId")
    private String callId;

    @JsonProperty("command")
    private String command;

    @JsonProperty("args")
    private String[] args;

    @JsonProperty("envVars")
    private Map<String, String> envVars;

    /** JSON-RPC method: "tools/list" 或 "tools/call" */
    @JsonProperty("method")
    private String method;

    @JsonProperty("toolName")
    private String toolName;

    @JsonProperty("arguments")
    private Object arguments;
}
