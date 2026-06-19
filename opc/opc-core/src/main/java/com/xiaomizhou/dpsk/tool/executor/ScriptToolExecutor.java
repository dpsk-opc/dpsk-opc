package com.xiaomizhou.dpsk.tool.executor;

import com.xiaomizhou.dpsk.tool.ToolExecutor;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.extern.slf4j.Slf4j;

/**
 * 脚本执行器，动态执行 JS/Python 脚本（热插拔用）。
 * <p>
 * 当前为占位实现，后续集成脚本引擎。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class ScriptToolExecutor implements ToolExecutor {

    @Override
    public String execute(ToolCall call, ToolContext context, ToolMetadata metadata) throws Exception {
        log.warn("Script tool execution is not yet implemented. Tool: {}", call.getName());
        throw new UnsupportedOperationException("Script tool execution is not yet implemented. Tool: " + call.getName());
    }
}
