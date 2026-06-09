package com.xiaomizhou.dpsk.tool.executor;

import com.xiaomizhou.dpsk.tool.ToolExecutor;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 工具执行器，通过 MCP 协议调用远程工具服务。
 * <p>
 * 当前为占位实现，后续集成 MCP Client。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class McpToolExecutor implements ToolExecutor {

    @Override
    public String execute(ToolCall call, ToolContext context) throws Exception {
        log.warn("MCP tool execution is not yet implemented. Tool: {}", call.getName());
        throw new UnsupportedOperationException("MCP tool execution is not yet implemented. Tool: " + call.getName());
    }
}
