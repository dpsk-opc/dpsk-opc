package com.xiaomizhou.dpsk.agent.factory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.xiaomizhou.dpsk.agent.AgentBuildSpec;
import com.xiaomizhou.dpsk.agent.data.AgentDef;
import com.xiaomizhou.dpsk.memory.MemorySystem;
import com.xiaomizhou.dpsk.memory.assembler.ContextAssembler;
import com.xiaomizhou.dpsk.memory.config.MemoryConfig;
import com.xiaomizhou.dpsk.tool.LangChain4JToolBridge;
import com.xiaomizhou.dpsk.tool.ToolInvocationInterceptor;
import com.xiaomizhou.dpsk.tool.ToolRegistry;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.ToolUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * Agent 组件工厂，提供 Builder 共享的零件。
 * 每个零件方法接收构建参数，产出对应组件。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/3
 */
@Slf4j
public class AgentComponentFactory {

    private final MemorySystem memorySystem;
    private final ToolRegistry toolRegistry;
    private final ToolInvocationInterceptor toolInvocationInterceptor;
    private final ApplicationContext applicationContext;

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    /**
     * 工作空间边界解析器（可为 null，表示未启用工作空间能力）。
     */
    private final com.xiaomizhou.dpsk.tool.workspace.WorkspaceResolver workspaceResolver;

    /**
     * 构造工厂。
     *
     * @param memorySystem              记忆系统
     * @param toolRegistry              工具注册中心
     * @param toolInvocationInterceptor 工具调用拦截器
     * @param applicationContext        Spring 上下文
     * @param apiKey                    LLM API Key
     * @param baseUrl                   LLM API Base URL
     * @param modelName                 LLM 模型名称
     */
    public AgentComponentFactory(MemorySystem memorySystem,
                                 ToolRegistry toolRegistry,
                                 ToolInvocationInterceptor toolInvocationInterceptor,
                                 ApplicationContext applicationContext,
                                 String apiKey,
                                 String baseUrl,
                                 String modelName) {
        this(memorySystem, toolRegistry, toolInvocationInterceptor, applicationContext,
                apiKey, baseUrl, modelName, null);
    }

    public AgentComponentFactory(MemorySystem memorySystem,
                                 ToolRegistry toolRegistry,
                                 ToolInvocationInterceptor toolInvocationInterceptor,
                                 ApplicationContext applicationContext,
                                 String apiKey,
                                 String baseUrl,
                                 String modelName,
                                 com.xiaomizhou.dpsk.tool.workspace.WorkspaceResolver workspaceResolver) {
        this.memorySystem = memorySystem;
        this.toolRegistry = toolRegistry;
        this.toolInvocationInterceptor = toolInvocationInterceptor;
        this.applicationContext = applicationContext;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.workspaceResolver = workspaceResolver;
    }

    /**
     * 解析执行期工作空间边界。
     *
     * @param agentWorkspace Agent 配置的 workspace
     * @param agentCode      Agent 编码（用于默认值推导）
     * @param sharedWorkspace 群 / 专家团公共产出目录（可为空）
     * @return 边界集合；未启用时返回空 scope
     */
    public com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope resolveWorkspace(
            String agentWorkspace, String agentCode, String sharedWorkspace) {
        if (workspaceResolver == null) {
            return com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope.builder().build();
        }
        return workspaceResolver.resolve(agentWorkspace, agentCode, sharedWorkspace);
    }

    /**
     * 构建流式 LLM 模型（单聊使用）
     */
    public OpenAiStreamingChatModel createStreamingModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .returnThinking(true)
                .sendThinking(true)
                .build();
    }

    public OpenAiImageModel createImageModel(String llmConfigJson, AgentBuildSpec.ImageBuildSpec imageBuildSpec) {
        LlmConfigOverride override = parseLlmConfig(llmConfigJson);
        if (Objects.isNull(override)) {
            return null;
        }

        if (AgentBuildSpec.ImageBuildSpec.TYPE_TEXT2IMAGE == imageBuildSpec.getMode()) {

            return OpenAiImageModel.builder()
                    .apiKey(coalesce(override.getAccessKey(), apiKey))
                    .baseUrl(coalesce(override.getBaseUrl(), baseUrl))
                    .modelName(coalesce(override.getModelName(), modelName))
                    .size(imageBuildSpec.getSize().replace("*", "x"))
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }

        List<String> urls = imageBuildSpec.getUrls();
        if (CollectionUtils.isEmpty(urls)) {
            log.warn("image to image mode urls can not be null.");
            return null;
        }

        Map<String, Object> body = Maps.newHashMap();

        body.put("image", urls.stream().map(url -> {

            // open url and convert to base64
            try {
                byte[] bytes = IOUtils.toByteArray(new URL(url).openStream());
                return "data:image/png;base64," + new String(Base64.getEncoder().encode(bytes));
            } catch (IOException e) {
                log.warn("image to image mode url open failed.", e);
                return null;
            }
        }).filter(Objects::nonNull).toList());


        Map<String, String> query = Maps.newHashMap();

        query.put("extra_body", JsonUtils.toJson(body));

        return OpenAiImageModel.builder()
                .apiKey(coalesce(override.getAccessKey(), apiKey))
                .baseUrl(coalesce(override.getBaseUrl(), baseUrl))
                .modelName(coalesce(override.getModelName(), modelName))
                .customQueryParams(query)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 构建流式 LLM 模型，支持 Agent 自定义配置。
     * 如果 llmConfigJson 为空，则退化到默认配置。
     *
     * @param llmConfigJson Agent 的 llm_config JSON 字符串
     */
    public OpenAiStreamingChatModel createStreamingModel(String llmConfigJson) {
        LlmConfigOverride override = parseLlmConfig(llmConfigJson);
        if (override == null) {
            return createStreamingModel();
        }

        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(coalesce(override.getAccessKey(), apiKey))
                .baseUrl(coalesce(override.getBaseUrl(), baseUrl))
                .modelName(coalesce(override.getModelName(), modelName))
                .logRequests(true)
                .timeout(Duration.ofMinutes(5))
//                .maxCompletionTokens(4096)
                .logResponses(true)
                .returnThinking(true)
                .sendThinking(true);

        if (override.getTemperature() != null) {
            builder.temperature(override.getTemperature());
        }
        if (override.getMaxTokens() != null) {
            builder.maxTokens(override.getMaxTokens());
        }
        if (override.getTopP() != null) {
            builder.topP(override.getTopP());
        }
        if (override.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(override.getFrequencyPenalty());
        }
        if (override.getPresencePenalty() != null) {
            builder.presencePenalty(override.getPresencePenalty());
        }
        if (override.getStop() != null && !override.getStop().isEmpty()) {
            builder.stop(override.getStop());
        }

        log.info("Created streaming model with agent-specific config: model={}, temperature={}, maxTokens={}",
                coalesce(override.getModelName(), modelName),
                override.getTemperature(),
                override.getMaxTokens());
        return builder.build();
    }

    /**
     * 构建同步 LLM 模型（群聊 Supervisor 使用）
     */
    public OpenAiChatModel createChatModel() {
        return OpenAiChatModel.builder()
                .modelName(modelName)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .logRequests(true)
                .build();
    }

    /**
     * 构建同步 LLM 模型，支持 Agent 自定义配置。
     * 如果 llmConfigJson 为空，则退化到默认配置。
     *
     * @param llmConfigJson Agent 的 llm_config JSON 字符串
     */
    public OpenAiChatModel createChatModel(String llmConfigJson) {
        LlmConfigOverride override = parseLlmConfig(llmConfigJson);
        if (override == null) {
            return createChatModel();
        }

        var builder = OpenAiChatModel.builder()
                .apiKey(coalesce(override.getAccessKey(), apiKey))
                .baseUrl(coalesce(override.getBaseUrl(), baseUrl))
                .modelName(coalesce(override.getModelName(), modelName))
                .logRequests(true);

        if (override.getTemperature() != null) {
            builder.temperature(override.getTemperature());
        }
        if (override.getMaxTokens() != null) {
            builder.maxTokens(override.getMaxTokens());
        }
        if (override.getTopP() != null) {
            builder.topP(override.getTopP());
        }
        if (override.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(override.getFrequencyPenalty());
        }
        if (override.getPresencePenalty() != null) {
            builder.presencePenalty(override.getPresencePenalty());
        }

        log.info("Created chat model with agent-specific config: model={}, temperature={}, maxTokens={}",
                coalesce(override.getModelName(), modelName),
                override.getTemperature(),
                override.getMaxTokens());
        return builder.build();
    }

    /**
     * 构建 ChatMemory（L0 工作记忆）
     */
    public ChatMemory createChatMemory(AgentBuildSpec spec) {
        String memoryId;

        String conversationCode = spec.getConversationCode();
        String groupCode = spec.getGroupCode();
        String agentCode = spec.getUserCode();

        String targetAgentCode = spec.getTargetAgentCode();
        String taskCode = spec.getTaskCode();

        ContextAssembler.AssembledPrompt prompt;
        if ((AgentBuildSpec.MODE_GROUP.equalsIgnoreCase(spec.getMode()))) {
            memoryId = MemoryConfig.buildGroupMemoryId(conversationCode, groupCode, agentCode);
            prompt = assembleSystemPrompt(spec);
        } else if (AgentBuildSpec.MODE_WORKFLOW.equalsIgnoreCase(spec.getMode())) {
            memoryId = MemoryConfig.buildWorkflowMemoryId(conversationCode, targetAgentCode, taskCode);
            prompt = assembleWorkflowSystemPrompt(spec);
        } else {
            memoryId = MemoryConfig.buildMemoryId(conversationCode, agentCode);
            prompt = assembleSystemPrompt(spec);
        }

        // 在 memoryId 末尾追加本次锚定的用户消息 code，供 L0 窗口在 UserMessage 被挤出时精确保回真实用户需求
        memoryId = MemoryConfig.appendUserMessageCode(memoryId, spec.getUserMessageCode());

        return MessageWindowChatMemory.builder()
                .maxMessages(MemoryConfig.L0_MAX_MESSAGES)
                .chatMemoryStore(memorySystem.getChatMemoryStore(prompt))
                .id(memoryId)
                .build();
    }

    /**
     * 获取 Agent 绑定的工具列表
     */
    public List<ToolProvider> getToolProviders(String agentCode, String userCode, String conversationCode, List<String> mcpCodes) {
        return getToolProviders(agentCode, userCode, conversationCode, mcpCodes, null);
    }

    /**
     * 获取 Agent 绑定的工具列表（带工作空间边界）。
     *
     * @param workspaceScope 工作空间边界集合，用于文件访问管控
     */
    public List<ToolProvider> getToolProviders(String agentCode, String userCode, String conversationCode,
                                               List<String> mcpCodes,
                                               com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope) {
        return getToolProviders(agentCode, userCode, conversationCode, mcpCodes, workspaceScope, null);
    }

    /**
     * 获取 Agent 绑定的工具列表（带工作空间边界与用户消息）。
     *
     * @param userContent 本轮用户消息文本，用于"用户明确给出路径即授权"的判定（D9）
     */
    public List<ToolProvider> getToolProviders(String agentCode, String userCode, String conversationCode,
                                               List<String> mcpCodes,
                                               com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope,
                                               String userContent) {
        LangChain4JToolBridge bridge = LangChain4JToolBridge.forAgent(
                toolRegistry, toolInvocationInterceptor, applicationContext, agentCode, userCode,
                conversationCode, mcpCodes, workspaceScope, userContent);
        return Collections.singletonList(bridge);
    }

    /**
     * 幻觉兜底策略工厂方法。
     * <p>
     * 模型可能不先 add_tools 就直接调用工具（幻觉），langchain4j 会因为工具不在本轮列表里
     * 而走到 hallucinatedToolNameStrategy。这里不再无脑要求 add_tools，而是
     * <b>先尝试真实执行</b>：执行成功就返回真实结果；只有工具确实不存在/不可用（或执行失败）
     * 时才回传引导信息，让模型决定调用 add_tools 或修正参数。
     *
     * @param agentCode        当前 Agent 编码（用于 Agent 作用域校验）
     * @param userCode         用户编码
     * @param conversationCode 会话编码
     * @param mcpCodes         本次允许的 MCP 编码
     */
    public Function<ToolExecutionRequest, ToolExecutionResultMessage> getToolExecutionResultMessageFunction(
            String agentCode, String userCode, String conversationCode, List<String> mcpCodes) {
        return getToolExecutionResultMessageFunction(agentCode, userCode, conversationCode, mcpCodes, null);
    }

    /**
     * 幻觉兜底策略工厂方法（带工作空间边界）。
     *
     * @param workspaceScope 工作空间边界集合，用于文件访问管控
     */
    public Function<ToolExecutionRequest, ToolExecutionResultMessage> getToolExecutionResultMessageFunction(
            String agentCode, String userCode, String conversationCode, List<String> mcpCodes,
            com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope) {
        return getToolExecutionResultMessageFunction(agentCode, userCode, conversationCode,
                mcpCodes, workspaceScope, null);
    }

    /**
     * 幻觉兜底策略工厂方法（带工作空间边界与用户消息）。
     *
     * @param userContent 本轮用户消息文本，用于"用户明确给出路径即授权"的判定（D9）
     */
    public Function<ToolExecutionRequest, ToolExecutionResultMessage> getToolExecutionResultMessageFunction(
            String agentCode, String userCode, String conversationCode, List<String> mcpCodes,
            com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope, String userContent) {
        return new ToolExecutionResultMessageFunction(agentCode, userCode, conversationCode,
                mcpCodes, workspaceScope, userContent);
    }

    /**
     * 幻觉工具名兜底：先尝试执行，失败再引导 add_tools。
     */
    public class ToolExecutionResultMessageFunction implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

        private final String agentCode;
        private final String userCode;
        private final String conversationCode;
        private final List<String> mcpCodes;
        private final com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope;
        private final String userContent;

        public ToolExecutionResultMessageFunction(String agentCode, String userCode,
                                                  String conversationCode, List<String> mcpCodes) {
            this(agentCode, userCode, conversationCode, mcpCodes, null, null);
        }

        public ToolExecutionResultMessageFunction(String agentCode, String userCode,
                                                  String conversationCode, List<String> mcpCodes,
                                                  com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope) {
            this(agentCode, userCode, conversationCode, mcpCodes, workspaceScope, null);
        }

        public ToolExecutionResultMessageFunction(String agentCode, String userCode,
                                                  String conversationCode, List<String> mcpCodes,
                                                  com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope workspaceScope,
                                                  String userContent) {
            this.agentCode = agentCode;
            this.userCode = userCode;
            this.conversationCode = conversationCode;
            this.mcpCodes = mcpCodes;
            this.workspaceScope = workspaceScope;
            this.userContent = userContent;
        }

        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
            String toolName = toolExecutionRequest.name();

            // add_tools 是元工具：它不在工具注册表中（由桥接器内联处理），也不参与工作空间校验。
            // 若走下面的拦截器路径会得到"工具不存在: add_tools"，因此这里直接交给桥接器执行。
            if (LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME.equalsIgnoreCase(toolName)) {
                return ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest,
                        executeMetaTool(toolExecutionRequest));
            }

            ToolExecutionResult result;
            try {
                // 带上完整的上下文与 MCP 范围，这样 ask_user 等依赖上下文参数的工具也能正常执行
                ToolCall toolCall = ToolUtils.toToolCall(toolExecutionRequest);
                result = toolInvocationInterceptor.execute(toolCall, ToolContext.builder()
                        .agentCode(agentCode)
                        .userCode(userCode)
                        .conversationCode(conversationCode)
                        .workspaceScope(workspaceScope)
                        .userContent(userContent)
                        .build());
            } catch (Exception e) {
                log.error("幻觉工具执行出错! toolExecutionRequest: {}", toolExecutionRequest, e);
                result = ToolExecutionResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR,
                        "工具执行异常: " + e.getMessage());
            }

            // 先尝试执行成功 —— 直接用真实结果，模型无需感知这是一次"幻觉调用"
            if (result != null && result.isSuccess()) {
                log.info("幻觉工具 '{}' 已成功执行，直接返回真实结果", toolName);
                return ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest, result.toLlmText());
            }

            if (result != null && result.isToolMissing()) {
                // 工具确实不存在 / 不可用：引导先 add_tools
                log.warn("幻觉工具 '{}' 不存在或不可用，引导模型先调用 {}", toolName, LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME);
                return ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest,
                        "执行错误，工具不存在或未加载，请先调用 [%s] 添加 [%s] 后再使用。".formatted(
                                LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME, toolName));
            }

            // 参数类错误：本次是"幻觉调用"（模型没先 add_tools 就直接调用，未拿到参数 schema），
            // 因此除了回传具体错误，还要引导模型先 add_tools 获取正确的参数定义。
            // 注意：仅对参数类错误这样引导；执行类错误（如文件不存在）提示 add_tools 会误导模型绕圈。
            if (result != null && isParameterError(result)) {
                log.warn("幻觉工具 '{}' 参数不合法，引导模型先调用 {} 获取参数定义",
                        toolName, LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME);
                return ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest,
                        "%s\n提示：你尚未通过 [%s] 添加该工具，可能没有拿到正确的参数定义。"
                                .formatted(result.toLlmText(), LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME)
                                + "请先调用 [%s] 添加 [%s]，再按其参数定义重新调用。".formatted(
                                LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME, toolName));
            }

            // 其它失败（执行报错）：回传错误信息让模型自行修正
            String message = result == null ? "工具执行未返回结果" : result.toLlmText();
            return ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest, message);
        }

        /**
         * 判断是否为参数类错误（需要引导模型先 add_tools 拿参数定义）。
         */
        private boolean isParameterError(ToolExecutionResult result) {
            return ToolExecutionResult.ERROR_PARAM_INVALID.equals(result.getErrorCode());
        }

        /**
         * 执行元工具（当前仅 add_tools）。
         * <p>
         * 元工具由 {@link LangChain4JToolBridge} 内联处理，不在工具注册表中，
         * 因此必须走桥接器而非拦截器。
         */
        private String executeMetaTool(ToolExecutionRequest toolExecutionRequest) {
            try {
                LangChain4JToolBridge bridge = LangChain4JToolBridge.forAgent(
                        toolRegistry, toolInvocationInterceptor, applicationContext,
                        agentCode, userCode, conversationCode, mcpCodes, workspaceScope);
                return bridge.execute(toolExecutionRequest, null);
            } catch (Exception e) {
                log.error("元工具执行出错! toolExecutionRequest: {}", toolExecutionRequest, e);
                return "工具执行失败: " + e.getMessage();
            }
        }
    }

    /**
     * 组装完整 System Prompt（人设 + L2 长期事实 + L1 摘要 + @引用 + 历史）
     */
    public ContextAssembler.AssembledPrompt assembleWorkflowSystemPrompt(/*AgentDef def,*/
            AgentBuildSpec spec) {
        ContextAssembler assembler = memorySystem.getContextAssembler();
        return assembler.assembledForWorkflow(spec);
    }

    /**
     * 组装完整 System Prompt（人设 + L2 长期事实 + L1 摘要 + @引用 + 历史）
     */
    public ContextAssembler.AssembledPrompt assembleSystemPrompt(/*AgentDef def,*/
            AgentBuildSpec spec) {
        ContextAssembler assembler = memorySystem.getContextAssembler();
        return assembler.assemble(spec);
    }

    /**
     * 注入 L2 长期事实到 System Prompt（群聊使用）
     */
    public String enrichSystemPrompt(AgentDef def, String targetCode) {
        return enrichSystemPrompt(def, targetCode, null);
    }

    /**
     * 注入 L2 长期事实 + 工作空间规则到 System Prompt（群聊使用）。
     * <p>
     * 工作空间规则依赖 {@code spec.primaryWorkspace} / {@code spec.sharedWorkspace}，
     * 调用前请确保已通过 {@link #applyWorkspace(AgentBuildSpec, String, String, String)} 回填。
     *
     * @param def   Agent 定义
     * @param targetCode 目标编码（群聊为群组编码）
     * @param spec  构建规范（用于注入工作空间边界规则）
     */
    public String enrichSystemPrompt(AgentDef def, String targetCode, AgentBuildSpec spec) {
        ContextAssembler assembler = memorySystem.getContextAssembler();
        return assembler.enrichSystemPrompt(def.toPersonaText(), def.getCode(), targetCode, spec);
    }

    /**
     * 解析工作空间边界并回填到 spec。
     * <p>
     * 必须回填，因为 system prompt 的工作空间规则依赖 {@code spec.primaryWorkspace}
     * 与 {@code spec.sharedWorkspace}；工具侧的边界校验也复用同一份解析结果，
     * 保证"提示词里告诉模型的路径"与"实际允许读写的路径"完全一致。
     *
     * @param spec           构建规范（原地修改）
     * @param agentWorkspace Agent 配置的 workspace（可为空）
     * @param agentCode      Agent 编码（用于默认值推导）
     * @param sharedWorkspace 群 / 专家团公共产出目录（可为空）
     * @return 解析后的边界集合
     */
    public com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope applyWorkspace(AgentBuildSpec spec,
                                                                            String agentWorkspace,
                                                                            String agentCode,
                                                                            String sharedWorkspace) {
        com.xiaomizhou.dpsk.tool.workspace.WorkspaceScope scope =
                resolveWorkspace(agentWorkspace, agentCode, sharedWorkspace);
        if (spec != null) {
            spec.setPrimaryWorkspace(scope.getPrimaryWorkspace());
            spec.setSharedWorkspace(sharedWorkspace);
        }
        return scope;
    }

    /**
     * 获取 MemorySystem 实例
     */
    public MemorySystem getMemorySystem() {
        return memorySystem;
    }

    /**
     * 获取 ToolRegistry 实例
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * 获取 ToolInvocationInterceptor 实例
     */
    public ToolInvocationInterceptor getToolInvocationInterceptor() {
        return toolInvocationInterceptor;
    }

    /**
     * 获取 ApplicationContext 实例
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 获取当前默认模型名称
     */
    public String getModelName() {
        return modelName;
    }

    // ---- 私有辅助方法 ----

    /**
     * 解析 JSON 字符串为 LlmConfigOverride，解析失败或为空时返回 null。
     */
    private LlmConfigOverride parseLlmConfig(String llmConfigJson) {
        if (llmConfigJson == null || llmConfigJson.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.toObj(llmConfigJson, LlmConfigOverride.class);
        } catch (Exception e) {
            log.warn("Failed to parse llmConfig JSON.", e);
            return null;
        }
    }

    /**
     * 返回第一个非空值，用于将 Agent 自定义配置覆盖到默认值之上。
     */
    private static <T> T coalesce(T value, T fallback) {
        if (value instanceof String s) {
            return s.isBlank() ? fallback : value;
        }
        return value != null ? value : fallback;
    }

    /**
     * Agent 自定义 LLM 配置的内部 DTO。
     * 对应 t_agent.llm_config 的 JSON 格式：
     * {"model":"gpt-3.5-turbo","access_key":"xxx","temperature":0.7,"max_tokens":1024,...}
     */
    @Data
    private static class LlmConfigOverride {


        private String modelName;

        @JsonProperty("key")
        private String accessKey;


        private String baseUrl;

        private Double temperature;


        private Integer maxTokens;


        private Double topP;


        private Double frequencyPenalty;


        private Double presencePenalty;

        private List<String> stop;
    }
}
