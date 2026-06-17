package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用结果 payload（前端 Electron -> 后端）。
 * <p>
 * 支持两种响应格式：
 * <ul>
 *   <li>tools/call 结果：{ callId, success, result: "...", error: "..." }</li>
 *   <li>tools/list 结果：{ callId, success, tools: [{ name, description, inputSchema }] }</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpResultPayload {

    @JsonProperty("callId")
    private String callId;

    @JsonProperty("success")
    private boolean success;

    /** tools/call 的返回结果字符串 */
    @JsonProperty("result")
    private String result;

    /** tools/list 返回的工具列表 */
    @JsonProperty("tools")
    private List<Map<String, Object>> tools;

    @JsonProperty("error")
    private String error;
}
