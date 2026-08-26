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
     * 构造工厂。
     *
     * @param memorySystem           记忆系统
     * @param toolRegistry           工具注册中心
     * @param toolInvocationInterceptor 工具调用拦截器
     * @param applicationContext     Spring 上下文
     * @param apiKey                 LLM API Key
     * @param baseUrl                LLM API Base URL
     * @param modelName              LLM 模型名称
     */
    public AgentComponentFactory(MemorySystem memorySystem,
                                 ToolRegistry toolRegistry,
                                 ToolInvocationInterceptor toolInvocationInterceptor,
                                 ApplicationContext applicationContext,
                                 String apiKey,
                                 String baseUrl,
                                 String modelName) {
        this.memorySystem = memorySystem;
        this.toolRegistry = toolRegistry;
        this.toolInvocationInterceptor = toolInvocationInterceptor;
        this.applicationContext = applicationContext;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    /** 构建流式 LLM 模型（单聊使用） */
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
                    .size(imageBuildSpec.getSize().replace("*","x"))
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
                .maxCompletionTokens(4096)
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

    /** 构建同步 LLM 模型（群聊 Supervisor 使用） */
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
                .baseUrl(baseUrl)
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

    /** 构建 ChatMemory（L0 工作记忆） */
    public ChatMemory createChatMemory(AgentBuildSpec spec) {
        Object memoryId;

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

        return MessageWindowChatMemory.builder()
                .maxMessages(MemoryConfig.L0_MAX_MESSAGES)
                .chatMemoryStore(memorySystem.getChatMemoryStore(prompt))
                .id(memoryId)
                .build();
    }

    /**
     * 获取 Agent 绑定的工具列表
     */
    public List<ToolProvider> getToolProviders(String agentCode, String userCode, String conversationCode,List<String> mcpCodes) {
        LangChain4JToolBridge bridge = LangChain4JToolBridge.forAgent(
                toolRegistry, toolInvocationInterceptor, applicationContext, agentCode, userCode, conversationCode, mcpCodes);
        return Collections.singletonList(bridge);
    }

    public Function<ToolExecutionRequest, ToolExecutionResultMessage> getToolExecutionResultMessageFunction() {
        return new ToolExecutionResultMessageFunction(toolInvocationInterceptor);
    }

    public static class ToolExecutionResultMessageFunction implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

        private final ToolInvocationInterceptor toolInvocationInterceptor;

        public ToolExecutionResultMessageFunction(ToolInvocationInterceptor toolInvocationInterceptor) {
            this.toolInvocationInterceptor = toolInvocationInterceptor;
        }


        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
            ToolCall toolCall = ToolUtils.toToolCall(toolExecutionRequest);
            ToolExecutionResult result = toolInvocationInterceptor.execute(toolCall, ToolContext.builder().build());
            return ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest, null == result.getResult() ? "未知异常" : result.getResult());
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

    /** 注入 L2 长期事实到 System Prompt（群聊使用） */
    public String enrichSystemPrompt(AgentDef def, String targetCode) {
        ContextAssembler assembler = memorySystem.getContextAssembler();
        return assembler.enrichSystemPrompt(def.toPersonaText(), def.getCode(), targetCode);
    }

    /** 获取 MemorySystem 实例 */
    public MemorySystem getMemorySystem() {
        return memorySystem;
    }

    /** 获取 ToolRegistry 实例 */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /** 获取 ToolInvocationInterceptor 实例 */
    public ToolInvocationInterceptor getToolInvocationInterceptor() {
        return toolInvocationInterceptor;
    }

    /** 获取 ApplicationContext 实例 */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /** 获取当前默认模型名称 */
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
