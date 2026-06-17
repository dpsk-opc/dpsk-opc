package com.xiaomizhou.dpsk.db;

import com.xiaomizhou.dpsk.tool.executor.McpElectronBridge;
import com.xiaomizhou.dpsk.tool.executor.McpToolExecutor;
import com.xiaomizhou.dpsk.tool.executor.ToolExecutorRouter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 配置，在 im 模块启动后将完整配置的 McpToolExecutor 注入 ToolExecutorRouter。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/16
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class McpConfiguration {

    private final ToolExecutorRouter toolExecutorRouter;
    private final McpBindingRepositoryImpl bindingRepository;
    private final McpElectronBridge electronBridge;

    @PostConstruct
    public void initMcpExecutor() {
        McpToolExecutor mcpExecutor = new McpToolExecutor(bindingRepository, electronBridge);
        toolExecutorRouter.setMcpExecutor(mcpExecutor);
        log.info("McpToolExecutor configured with BindingRepository and ElectronBridge");
    }
}
