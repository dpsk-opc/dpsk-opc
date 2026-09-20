package com.xiaomizhou.dpsk.agent.builder;

import com.xiaomizhou.dpsk.agent.*;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.agent.data.AgentDefProvider;
import com.xiaomizhou.dpsk.agent.factory.AgentComponentFactory;
import com.xiaomizhou.dpsk.tool.model.ToolResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 群聊模式 Builder。
 * <p>
 * 由 opc-im 决策层（GroupResponderPicker）产出本次发言顺序（spec.responderAgentCodes），
 * 本 Builder 为候选 Agent 生成独立流式 UntypedAgent，并按其顺序【串行逐个流式】执行，
 * 复用 {@link StreamingAgentRunner} 处理完整流式事件。
 * <p>
 * 替代原 SupervisorAgent 黑盒机制，精确控制参与会话的 Agent，且支持流式输出。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
@RequiredArgsConstructor
public class GroupBuilder implements AgentBuilder {

    private final AgentDefProvider agentDefProvider;
    private final AgentComponentFactory factory;

    @Override
    public String supportedMode() {
        return AgentBuildSpec.MODE_GROUP;
    }

    @Override
    public AgentPipeline build(AgentBuildSpec spec) {
        List<String> targetAgentCodes = spec.getTargetAgentCodes();
        if (CollectionUtils.isEmpty(targetAgentCodes)) {
            throw new IllegalArgumentException("GroupBuilder requires targetAgentCodes");
        }

        // 1. 批量查询 Agent 定义
        List<AgentDef> agentDefs = agentDefProvider.getByCodes(targetAgentCodes);
        if (CollectionUtils.isEmpty(agentDefs)) {
            throw new IllegalArgumentException("No agents found for codes: " + targetAgentCodes);
        }

        // 2. 为每个候选 Agent 构建独立流式 UntypedAgent
        Map<String, UntypedAgent> agentMap = agentDefs.stream().collect(Collectors.toMap(
                AgentDef::getCode,
                agentDef -> buildStreamingAgent(agentDef, spec),
                (a, b) -> a,
                LinkedHashMap::new
        ));

        // 3. 发言顺序：决策层（GroupResponderPicker）产出的 responderAgentCodes，空则退化为候选顺序
        List<String> responders = spec.getResponderAgentCodes();
        if (CollectionUtils.isEmpty(responders)) {
            responders = new ArrayList<>(agentMap.keySet());
        }

        return new GroupPipeline(agentMap, agentDefs, responders);
    }

    /**
     * 为单个候选 Agent 构建流式 UntypedAgent（对齐 WorkflowBuilder.init() 的 AgenticServices 配置）。
     */
    private UntypedAgent buildStreamingAgent(AgentDef agentDef, AgentBuildSpec spec) {
        // 群聊下 spec.getTargetAgentCode() 为 null，会导致 createChatMemory → assembleSystemPrompt 拼不出 agent 人设。
        // 为每个 Agent 复制一份 spec 并注入当前 agent 的 targetAgentCode，让人设（[你的信息]）能拼进 system prompt。
        AgentBuildSpec agentSpec = copyWithTargetAgentCode(spec, agentDef.getCode());

        // 群聊记忆：每个 Agent 在群聊中有独立的记忆空间
        // 流式模型（优先使用 Agent 自定义 LLM 配置）
        OpenAiStreamingChatModel model = factory.createStreamingModel(agentDef.getLlmConfig());

        // 解析工作空间边界并回填 spec（成员自身 workspace 为主边界 + 群 workspace 作为额外可写公共产出目录）
        com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope = factory.applyWorkspace(
                spec, agentDef.getWorkspace(), agentDef.getCode(), spec.getSharedWorkspace());

        // 注入 L2 长期事实 + 工作空间规则（须在 applyWorkspace 之后，规则依赖 spec 上的 workspace 字段）
        String enrichedPersona = factory.enrichSystemPrompt(agentDef, spec.getGroupCode(), spec);

        return AgenticServices.agentBuilder()
                .streamingChatModel(model)
                .name(agentDef.getCode())
                // 显式注入人设 + L2 长期事实 + 工作空间规则，作为 system prompt
                .systemMessage(enrichedPersona)
                .toolProviders(factory.getToolProviders(agentDef.getCode(), spec.getUserCode(),
                        spec.getConversationCode(), spec.getMcpCodes(), workspaceScope, spec.getUserContent()))
                .userMessage(spec.getUserContent())
                .toolExecutionErrorHandler(new ToolExecutionErrorHandler() {
                    @Override
                    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
                        log.error("tool execution error:", error);
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
                        agentDef.getCode(), spec.getUserCode(), spec.getConversationCode(), spec.getMcpCodes(),
                        workspaceScope, spec.getUserContent()))
                .chatMemoryProvider(memoryId -> factory.createChatMemory(agentSpec))
                .returnType(TokenStream.class)
                .maxToolCallingRoundTrips(25)
                .build();
    }

    /**
     * 复制 spec 并注入 targetAgentCode。
     * 群聊下原 spec 的 targetAgentCode 为 null，这里按当前 agent 覆盖，使 assembleSystemPrompt 能拼出该 agent 人设。
     */
    private AgentBuildSpec copyWithTargetAgentCode(AgentBuildSpec spec, String agentCode) {
        return spec.toBuilder().targetAgentCode(agentCode).build();
    }

    /**
     * 群聊 Pipeline 实现：按决策层产出的发言顺序串行逐个流式执行。
     */
    private static class GroupPipeline implements AgentPipeline {

        /** agentCode → UntypedAgent */
        private final Map<String, UntypedAgent> agentMap;
        private final List<AgentDef> agentDefs;
        /** 本次发言顺序（由决策层产出，已保序） */
        private final List<String> responders;

        GroupPipeline(Map<String, UntypedAgent> agentMap, List<AgentDef> agentDefs, List<String> responders) {
            this.agentMap = agentMap;
            this.agentDefs = agentDefs;
            this.responders = responders;
        }

        @Override
        public PipelineResult execute(AgentCallback callback) {
            if (CollectionUtils.isEmpty(responders)) {
                log.warn("GroupPipeline no responder resolved, skip execution");
                return PipelineResult.builder().success(true).outputText(null).build();
            }

            // 汇总执行结果
            StringBuilder output = new StringBuilder();
            boolean anyFailed = false;
            String firstError = null;

            for (String code : responders) {
                // 中途取消 → 跳过后续所有 Agent
                if (callback.isCancelled()) {
                    log.info("GroupPipeline cancelled, skip remaining agents, current={}", code);
                    break;
                }

                UntypedAgent agent = agentMap.get(code);
                AgentDef agentDef = findAgentDef(code);
                if (agent == null || agentDef == null) {
                    log.warn("GroupPipeline cannot resolve agent for code={}, skip", code);
                    continue;
                }

                // 标记"谁在说"
                if (callback instanceof GroupAgentCallback groupCallback) {
                    groupCallback.setStreamCode(UUID.randomUUID().toString().replace("-", ""));
                    groupCallback.setSenderInfo(code);
                }

                log.info("GroupPipeline streaming agent start, code={}", code);
                PipelineResult result = StreamingAgentRunner.run(agent, agentDef, callback);
                log.info("GroupPipeline streaming agent end, code={}, success={}", code, result.isSuccess());

                if (!result.isSuccess()) {
                    anyFailed = true;
                    if (firstError == null) {
                        firstError = result.getErrorMessage();
                    }
                }
                if (result.getOutputText() != null) {
                    output.append(result.getOutputText()).append("\n");
                }
            }

            boolean cancelled = callback.isCancelled();
            if (cancelled) {
                return PipelineResult.builder().success(false).outputText(null).build();
            }

            return PipelineResult.builder()
                    .success(!anyFailed)
                    .outputText(output.length() > 0 ? output.toString().strip() : null)
                    .errorMessage(firstError)
                    .build();
        }

        private AgentDef findAgentDef(String code) {
            return agentDefs.stream().filter(d -> d.getCode().equals(code)).findFirst().orElse(null);
        }
    }
}
