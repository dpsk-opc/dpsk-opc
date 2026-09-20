package com.xiaomizhou.dpsk.tool;

import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.tool.buildin.CommandTools;
import com.xiaomizhou.dpsk.tool.buildin.FileTools;
import com.xiaomizhou.dpsk.tool.buildin.LoadSkillTools;
import com.xiaomizhou.dpsk.tool.executor.ToolExecutorRouter;
import com.xiaomizhou.dpsk.tool.repository.ToolAuditLogRepository;
import com.xiaomizhou.dpsk.tool.repository.ToolRepository;
import com.xiaomizhou.dpsk.tool.workspace.PathAccessConfirmer;
import com.xiaomizhou.dpsk.tool.workspace.PathAccessValidator;
import com.xiaomizhou.dpsk.tool.workspace.PathExtractionLlm;
import com.xiaomizhou.dpsk.tool.workspace.PathExtractor;
import com.xiaomizhou.dpsk.tool.workspace.PathGuard;
import com.xiaomizhou.dpsk.tool.workspace.ProtectedPathPolicy;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceAuthorizationStore;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceInitializer;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceProperties;
import com.xiaomizhou.dpsk.tool.workspace.WorkspaceResolver;
import dev.langchain4j.community.tool.webscraper.WebScraperTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
@EnableConfigurationProperties({ToolConfiguration.PrivateProperties.class,
        com.xiaomizhou.dpsk.tool.workspace.WorkspaceProperties.class})
public class ToolConfiguration {

    /**
     * 私有密钥配置，供工具参数中的敏感占位符替换使用。
     * <p>
     * 配置示例（application.properties）：
     * <pre>
     * com.xiaomizhou.dpsk.opc.privatekey.properties.AGNES_IMG_PRIVATE_KEY=sk-xxx
     * </pre>
     * 其中 map 的 key（如 AGNES_IMG_PRIVATE_KEY）是参数值中的占位符，
     * value 是执行时替换进去的真实密钥。
     */
    @ConfigurationProperties(prefix = "com.xiaomizhou.dpsk.opc.privatekey")
    public static class PrivateProperties {
        private Map<String, String> properties;

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }


    @Configuration
    @Slf4j
    @Component("toolInit")
    public static class BuildingInToolInitConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FileTools fileTools(ToolRegistry toolRegistry) {
            return new FileTools();
        }

        @Bean
        @ConditionalOnMissingBean
        public WebScraperTool webScraperTool() {
            return new WebScraperTool();
        }

        @Bean
        @ConditionalOnMissingBean
        public CommandTools commandTools() {
            return new CommandTools();
        }

        @Bean
        @ConditionalOnMissingBean
        public LoadSkillTools loadSkillTools() {
            return new LoadSkillTools();
        }

//        @Bean("playwright")
//        @ConditionalOnMissingBean
//        public BrowserUseTool playwright() {
//            Playwright playwright = Playwright.create();
//            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
//                    .setHeadless(false)
//                    .setChannel("chrome")
//                    .setChromiumSandbox(true)
//                    .setSlowMo(500);
//            Browser browser = playwright.chromium().launch(options);
//
//            return BrowserUseTool.from(PlaywrightBrowserExecutionEngine.builder().browser(browser).build());
//        }

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
            ToolConfirmationManager toolConfirmationManager,
            PrivateProperties privateProperties,
            ObjectProvider<PathAccessValidator> pathAccessValidatorProvider) {
        return new ToolInvocationInterceptor(toolRegistry, toolAuditLogger, toolConfirmationManager,
                new PrivateParameterReplacer(privateProperties.properties),
                pathAccessValidatorProvider.getIfAvailable());
    }

    // ==================== 工作空间（文件访问边界） ====================

    // WorkspaceProperties 由类上的 @ConfigurationProperties +
    // 本类的 @EnableConfigurationProperties 注册并绑定，无需在此重复定义 @Bean
    // （若用 @Bean 手工 new，属性不会绑定，会导致 root 为空、工作空间解析失败）。

    @Bean
    @ConditionalOnMissingBean
    public ProtectedPathPolicy protectedPathPolicy(WorkspaceProperties properties) {
        return new ProtectedPathPolicy(properties.isProtectedPathEnabled(), properties.getProtectedPathPatterns());
    }

    @Bean
    @ConditionalOnMissingBean
    public PathGuard pathGuard(ProtectedPathPolicy protectedPathPolicy) {
        return new PathGuard(protectedPathPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkspaceResolver workspaceResolver(WorkspaceProperties properties) {
        return new WorkspaceResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkspaceInitializer workspaceInitializer(WorkspaceProperties properties) {
        return new WorkspaceInitializer(properties);
    }

    /**
     * 路径抽取器：复用 opc-im 提供的 {@link PathExtractionLlm}（D17）。
     * <p>
     * 未提供实现时自动降级为"不启用抽取"，此时未声明工具一律走确认流程（D13）。
     */
    @Bean
    @ConditionalOnMissingBean
    public PathExtractor pathExtractor(ObjectProvider<PathExtractionLlm> llmProvider,
                                       WorkspaceProperties properties) {
        PathExtractionLlm llm = llmProvider.getIfAvailable();
        boolean enabled = properties.isEnabled() && llm != null;
        if (llm == null) {
            log.warn("PathExtractionLlm not provided: un-declared tools (MCP/script/command) will "
                    + "always require user confirmation for file access");
        }
        return new PathExtractor(llm, enabled);
    }

    /**
     * 用户消息路径授权匹配器（D9）：用户消息里明确给出的路径视为授权。
     */
    @Bean
    @ConditionalOnMissingBean
    public com.xiaomizhou.dpsk.tool.workspace.UserPathAuthorizationMatcher userPathAuthorizationMatcher() {
        return new com.xiaomizhou.dpsk.tool.workspace.UserPathAuthorizationMatcher();
    }

    /**
     * 路径访问校验服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public PathAccessValidator pathAccessValidator(PathGuard pathGuard,
                                                   PathExtractor pathExtractor,
                                                   ObjectProvider<PathAccessConfirmer> confirmerProvider,
                                                   ObjectProvider<WorkspaceAuthorizationStore> authorizationStoreProvider,
                                                   WorkspaceProperties properties,
                                                   com.xiaomizhou.dpsk.tool.workspace.UserPathAuthorizationMatcher userPathMatcher) {
        return new PathAccessValidator(pathGuard, pathExtractor,
                confirmerProvider.getIfAvailable(), authorizationStoreProvider.getIfAvailable(),
                properties, userPathMatcher);
    }

    @Bean
    @DependsOn({"toolRegistry", "toolInit"})
    @ConditionalOnMissingBean
    public ToolAutoRegistrar toolAutoRegistrar(
            ApplicationContext applicationContext,
            ToolRepository toolRepository,
            ToolRegistry toolRegistry) {
        ToolAutoRegistrar registrar = new ToolAutoRegistrar(applicationContext, toolRepository, toolRegistry);
        // 注意：这里【不能】直接调用 scanAndRegister()。
        // 原因：@Bean 方法在容器刷新早期执行，此时 Flyway 尚未完成数据库迁移
        // （FlywayMigrationInitializer 在刷新后期才触发），会导致 t_tool 表缺少新列而注册失败。
        // 因此注册推迟到 ApplicationReadyEvent（见 ToolRegistrationInitializer）。
        return registrar;
    }

    /**
     * 工具注册初始化器：在应用就绪后执行工具扫描与注册。
     * <p>
     * 执行时机晚于 Flyway 数据库迁移（Flyway 在容器刷新阶段完成，本事件在其之后触发），
     * 保证 t_tool 表结构与迁移脚本一致。
     * <p>
     * 同时显式依赖 {@code FlywayMigrationInitializer}（存在时），进一步保证顺序。
     */
    @Component
    @Slf4j
    @org.springframework.core.annotation.Order(0)
    public static class ToolRegistrationInitializer implements ApplicationListener<ApplicationReadyEvent> {

        private final ToolAutoRegistrar toolAutoRegistrar;

        private final ToolRegistry toolRegistry;

        public ToolRegistrationInitializer(ToolAutoRegistrar toolAutoRegistrar, ToolRegistry toolRegistry) {
            this.toolAutoRegistrar = toolAutoRegistrar;
            this.toolRegistry = toolRegistry;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            try {
                // ApplicationReadyEvent 在容器刷新完成后触发，此时 Flyway 迁移（刷新阶段执行）必定已完成，
                // 保证 t_tool 表结构与迁移脚本一致。
                toolAutoRegistrar.scanAndRegister();
                // 注册完成后 reload 到内存，保证运行时能读到新增的 filesystem_access / path_params
                toolRegistry.reload();
                log.info("Tool registration completed after application ready");
            } catch (Exception e) {
                log.error("Tool auto-registration failed after application ready", e);
            }
        }
    }
}
