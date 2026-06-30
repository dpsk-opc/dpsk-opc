package com.xiaomizhou.dpsk.config;

import com.xiaomizhou.dpsk.agent.AgentOrchestrator;
import com.xiaomizhou.dpsk.agent.builder.GroupBuilder;
import com.xiaomizhou.dpsk.agent.builder.SingleBuilder;
import com.xiaomizhou.dpsk.agent.builder.WorkflowBuilder;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.memory.MemorySystem;
import com.xiaomizhou.dpsk.tool.ToolInvocationInterceptor;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 编排引擎 Spring 配置。
 * <p>
 * 负责创建和装配 opc-core 的编排引擎所需的所有组件：
 * <ul>
 *   <li>AgentComponentFactory — 共享零件工厂</li>
 *   <li>SingleBuilder — 单聊模式构建器</li>
 *   <li>GroupBuilder — 群聊模式构建器</li>
 *   <li>WorkflowBuilder — 工作流模式构建器（预留）</li>
 *   <li>AgentOrchestrator — 唯一条目编排器</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Configuration
@Slf4j
public class AgentOrchestrationConfiguration {

    @Value("${com.xiaomizhou.opc.llm.api-key}")
    private String apiKey;

    @Value("${com.xiaomizhou.opc.llm.base-url}")
    private String baseUrl;

    @Value("${com.xiaomizhou.opc.llm.model-name}")
    private String modelName;

    @Bean
    public AgentComponentFactory agentComponentFactory(
            MemorySystem memorySystem,
            ToolRegistry toolRegistry,
            ToolInvocationInterceptor toolInvocationInterceptor,
            ApplicationContext applicationContext) {
        log.info("Creating AgentComponentFactory with model={}, baseUrl={}", modelName, baseUrl);
        return new AgentComponentFactory(
                memorySystem,
                toolRegistry,
                toolInvocationInterceptor,
                applicationContext,
                apiKey,
                baseUrl,
                modelName);
    }

    @Bean
    public SingleBuilder singleBuilder(AgentDefProvider agentDefProvider,
                                        AgentComponentFactory factory) {
        log.info("Creating SingleBuilder");
        return new SingleBuilder(agentDefProvider, factory);
    }

    @Bean
    public GroupBuilder groupBuilder(AgentDefProvider agentDefProvider,
                                      AgentComponentFactory factory) {
        log.info("Creating GroupBuilder");
        return new GroupBuilder(agentDefProvider, factory);
    }

    @Bean
    public WorkflowBuilder workflowBuilder(AgentDefProvider agentDefProvider,
                                           AgentComponentFactory factory) {
        log.info("Creating WorkflowBuilder (reserved)");
        return new WorkflowBuilder(agentDefProvider, factory);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator(SingleBuilder singleBuilder,
                                                GroupBuilder groupBuilder,
                                                WorkflowBuilder workflowBuilder) {
        log.info("Creating AgentOrchestrator with builders: SINGLE, GROUP, WORKFLOW");
        AgentOrchestrator orchestrator = new AgentOrchestrator();
        orchestrator.registerBuilder(singleBuilder);
        orchestrator.registerBuilder(groupBuilder);
        orchestrator.registerBuilder(workflowBuilder);
        return orchestrator;
    }
}
