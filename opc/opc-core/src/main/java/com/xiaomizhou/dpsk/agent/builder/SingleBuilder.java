package com.xiaomizhou.dpsk.agent.builder;

import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.tool.model.ToolResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 单聊模式 Builder。
 * 构建单个 Agent 的流式对话 Pipeline。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
@RequiredArgsConstructor
public class SingleBuilder implements AgentBuilder {

    private final AgentDefProvider agentDefProvider;
    private final AgentComponentFactory factory;

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_SINGLE;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {
        String targetAgentCode = spec.getTargetAgentCode();
        if (targetAgentCode == null || targetAgentCode.isEmpty()) {
            throw new IllegalArgumentException("SingleBuilder requires targetAgentCode");
        }

        // 1. 查询 Agent 定义
        AgentDef agentDef = agentDefProvider.getByCode(targetAgentCode);
        if (agentDef == null) {
            throw new IllegalArgumentException("Agent not found: " + targetAgentCode);
        }

        // 2. 构建 LLM 流式模型（优先使用 Agent 自定义配置）
        OpenAiStreamingChatModel model = factory.createStreamingModel(agentDef.getLlmConfig());

        // 3. 构建 ChatMemory
        ChatMemory chatMemory = factory.createChatMemory(spec);

        // 4. 解析工作空间边界并回填 spec（单聊以当前 Agent 自己的 workspace 为边界）
        //    回填后 system prompt 才能注入"工作空间 + 目录约定 + 越界规则"
        com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope = factory.applyWorkspace(
                spec, agentDef.getWorkspace(), targetAgentCode, null);
        List<ToolProvider> toolProviders = factory.getToolProviders(targetAgentCode, spec.getUserCode(),
                spec.getConversationCode(), spec.getMcpCodes(), workspaceScope, spec.getUserContent());

        // 5. 组装 system prompt（人设 + 记忆 + 工作空间规则）
        ContextAssembler.AssembledPrompt assembledPrompt = factory.assembleSystemPrompt(spec);


        // 6. 构建 AgenticServices Agent
        UntypedAgent agent = AgenticServices.agentBuilder()
                .streamingChatModel(model)
                .name(agentDef.getName())
                // 显式注入 system prompt（人设 + 记忆 + 工作空间规则）
                .systemMessage(assembledPrompt.getSystemPart())
                .toolProviders(toolProviders)
                .userMessage(spec.getUserContent())
                .toolExecutionErrorHandler(new ToolExecutionErrorHandler() {
                    @Override
                    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
                        log.error("tool execution error:", error);
                        // 回传具体的错误类型与原因，让模型能据此调整，而不是反复重试同一调用
                        return ToolErrorHandlerResult.text(
                                "工具执行失败: " + ToolResult.describeError(error) + "。请检查参数或换一种方式重试，不要重复相同调用。");
                    }
                })
                .toolArgumentsErrorHandler(new ToolArgumentsErrorHandler() {
                    @Override
                    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
                        log.error("tool arguments error:", error);
                        return ToolErrorHandlerResult.text(
                                "工具参数错误: " + ToolResult.describeError(error) + "。请修正参数格式后重试。");
                    }
                })

                // 幻觉情况 => 先尝试执行，失败再引导 add_tools
                .hallucinatedToolNameStrategy(factory.getToolExecutionResultMessageFunction(
                        targetAgentCode, spec.getUserCode(), spec.getConversationCode(), spec.getMcpCodes(),
                        workspaceScope, spec.getUserContent()))
                .chatMemoryProvider(memoryId -> chatMemory)
                .returnType(TokenStream.class)
                .maxToolCallingRoundTrips(25)
                .build();

        return new SinglePipeline(agent, agentDef);
    }

    /**
     * 单聊 Pipeline 实现。
     * 流式事件处理统一复用 {@link StreamingAgentRunner}。
     */
    @Slf4j
    private static class SinglePipeline implements AgentPipeline {

        private final UntypedAgent agent;
        private final AgentDef agentDef;


        SinglePipeline(UntypedAgent agent, AgentDef agentDef) {
            this.agent = agent;
            this.agentDef = agentDef;
        }


        @Override
        public PipelineResult execute(AgentCallback callback) {
            return StreamingAgentRunner.run(agent, agentDef, callback);
        }
    }
}
