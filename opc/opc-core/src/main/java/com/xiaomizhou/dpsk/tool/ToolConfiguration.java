package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.buildin.DateTimeTools;
import com.xiaomizhou.dpsk.tool.buildin.FileTools;
import com.xiaomizhou.dpsk.tool.buildin.SearchTools;
import com.xiaomizhou.dpsk.tool.executor.ToolExecutorRouter;
import com.xiaomizhou.dpsk.tool.repository.ToolAuditLogRepository;
import com.xiaomizhou.dpsk.tool.repository.ToolRepository;
import dev.langchain4j.community.tool.webscraper.WebScraperTool;
import dev.langchain4j.web.search.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * 工具系统自动配置。
 * <p>
 * 启动时自动：
 * 1. 创建 ToolRegistry、各执行器、拦截器等核心组件
 * 2. 执行 ToolAutoRegistrar 扫描 @Tool 注解并自动注册
 * 3. 从 DB reload 工具到内存
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Configuration
@Slf4j
public class ToolConfiguration {

    @Configuration
    @Slf4j
    @Component("toolInit")
    public static class BuildingInToolInitConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SearchTools searchTools(ToolRegistry toolRegistry) {
            return new SearchTools(toolRegistry);
        }

        @Bean
        @ConditionalOnMissingBean
        public FileTools fileTools(ToolRegistry toolRegistry) {
            return new FileTools();
        }

        @Bean
        @ConditionalOnMissingBean
        public DateTimeTools dateTimeTools() {
            return new DateTimeTools();
        }

        @Bean
        @ConditionalOnMissingBean
        public WebScraperTool webScraperTool() {
            return new WebScraperTool();
        }

    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutorRouter toolExecutorRouter(ApplicationContext applicationContext) {
        return ToolExecutorRouter.createDefault(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ToolRepository toolRepository, ToolExecutorRouter toolExecutorRouter) {
        return new ToolRegistry(toolRepository, toolExecutorRouter);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolAuditLogger toolAuditLogger(ToolAuditLogRepository auditLogRepository) {
        return new ToolAuditLogger(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolConfirmationManager toolConfirmationManager() {
        return new ToolConfirmationManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolInvocationInterceptor toolInvocationInterceptor(
            ToolRegistry toolRegistry,
            ToolAuditLogger toolAuditLogger,
            ToolConfirmationManager toolConfirmationManager) {
        return new ToolInvocationInterceptor(toolRegistry, toolAuditLogger, toolConfirmationManager);
    }

    @Bean
    @DependsOn({"toolRegistry", "toolInit"})
    @ConditionalOnMissingBean
    public ToolAutoRegistrar toolAutoRegistrar(
            ApplicationContext applicationContext,
            ToolRepository toolRepository,
            ToolRegistry toolRegistry) {
        ToolAutoRegistrar registrar = new ToolAutoRegistrar(applicationContext, toolRepository, toolRegistry);
        // 启动时自动扫描并注册
        try {
            registrar.scanAndRegister();
        } catch (Exception e) {
            log.warn("Tool auto-registration encountered an error (may be normal if DB not ready)", e);
        }
        return registrar;
    }
}
