package com.xiaomizhou.dpsk.tool.executor;

import com.xiaomizhou.dpsk.tool.SourceType;
import com.xiaomizhou.dpsk.tool.ToolExecutor;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.model.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

/**
 * 工具执行器路由器，根据 sourceType 路由到对应的执行器。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class ToolExecutorRouter {

    private final LocalToolExecutor localExecutor;
    private volatile McpToolExecutor mcpExecutor;
    private final ScriptToolExecutor scriptExecutor;

    public ToolExecutorRouter(LocalToolExecutor localExecutor, McpToolExecutor mcpExecutor, ScriptToolExecutor scriptExecutor) {
        this.localExecutor = localExecutor;
        this.mcpExecutor = mcpExecutor;
        this.scriptExecutor = scriptExecutor;
    }

    /**
     * 创建默认路由器（需要 Spring ApplicationContext）。
     */
    public static ToolExecutorRouter createDefault(ApplicationContext applicationContext) {
        return new ToolExecutorRouter(
                new LocalToolExecutor(applicationContext),
                new McpToolExecutor(),
                new ScriptToolExecutor()
        );
    }

    /**
     * 设置 MCP 执行器（由 im 模块注入，可替换默认的无参实例）。
     */
    public void setMcpExecutor(McpToolExecutor mcpExecutor) {
        this.mcpExecutor = mcpExecutor;
    }

    /**
     * 根据 metadata 的 sourceType 路由并执行。
     *
     * @param metadata 工具元数据
     * @param call     调用请求
     * @param context  执行上下文
     * @return 结构化执行结果
     */
    public ToolResult execute(ToolMetadata metadata, ToolCall call, ToolContext context) throws Exception {
        // 将 sourceRef 注入到参数中供 LocalToolExecutor 使用
        if (metadata.getSourceRef() != null) {
            call.getParameters().put("__sourceRef__", metadata.getSourceRef());
        }

        ToolExecutor executor = select(metadata.getSourceType());
        log.debug("Routing tool '{}' (sourceType={}) to executor {}", 
                call.getName(), metadata.getSourceType(), executor.getClass().getSimpleName());
        return executor.execute(call, context, metadata);
    }

    /**
     * 根据来源类型选择执行器。
     */
    private ToolExecutor select(String sourceType) {
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType must not be null");
        }

        return switch (sourceType.toUpperCase()) {
            case SourceType.LOCAL -> localExecutor;
            case SourceType.MCP -> mcpExecutor;
            case SourceType.SCRIPT -> scriptExecutor;
            default -> throw new IllegalArgumentException("Unknown sourceType: " + sourceType);
        };
    }
}
