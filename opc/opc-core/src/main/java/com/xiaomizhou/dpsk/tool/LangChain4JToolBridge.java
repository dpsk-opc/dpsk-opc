package com.xiaomizhou.dpsk.tool;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.utils.ToolUtils;
import dev.langchain4j.agent.tool.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LangChain4j 工具桥接器 —— 将自定义工具体系与 LangChain4j 的 AiServices/AgenticServices 打通。
 *
 * <h3>设计目的</h3>
 * <pre>
 *   LC4j AiServices/AgenticServices
 *          │
 *          │  .toolProviders( new LangChain4jToolBridge(toolRegistry, interceptor, agentCode) )
 *          ▼
 *   LangChain4jToolBridge (implements ToolProvider)
 *          │
 *          │  provideTools()  → 从 ToolRegistry 读取 ToolMetadata → 转换为 ToolSpecification
 *          │  execute()       → 委托给 ToolInvocationInterceptor（含权限检查/参数补全/审计）
 *          ▼
 *   ToolInvocationInterceptor → ToolExecutorRouter → LOCAL / MCP / SCRIPT
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   // 在 AgenticServices 或 AiServices builder 中：
 *   LangChain4jToolBridge bridge = LangChain4jToolBridge.builder()
 *       .toolRegistry(toolRegistry)
 *       .interceptor(toolInvocationInterceptor)
 *       .agentCode("my-agent")
 *       .build();
 *
 *   var agent = AgenticServices.agentBuilder(MyAgent.class)
 *       .streamingChatModel(model)
 *       .toolProviders(bridge)         // ← 替代 .tools(new DateTimeTools())
 *       .maxSequentialToolsInvocations(10)
 *       .build();
 * }</pre>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/6/1
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class LangChain4JToolBridge implements ToolProvider {

    /**
     * 工具注册中心，提供所有已注册的工具元数据
     */
    private final ToolRegistry toolRegistry;

    /**
     * 工具调用拦截器，处理权限检查、参数补全、路由执行、审计
     */
    private final ToolInvocationInterceptor interceptor;


    private final ApplicationContext applicationContext;

    /**
     * 当前 Agent 编码，用于按 Agent 过滤工具列表和标识调用来源
     */
    @Builder.Default
    private final String agentCode = "";

    /**
     * 用户编码（可选，若未指定则从 ToolProviderRequest 中无法获取时使用此默认值）
     */
    @Builder.Default
    private final String userCode = "";

    /**
     * 会话编码（可选，若未指定则从 ToolProviderRequest 中无法获取时使用此默认值）
     */
    @Builder.Default
    private final String conversationCode = "";

    @Builder.Default
    private final List<String> mcpCodes = Lists.newArrayList();

    public static final String ADD_TOOLS_TOOL_NAME = "add_tools";

    public static final String TOOL_ARGUMENT = "toolNames";

    // ==================== ToolProvider 接口实现 ====================

    /**
     * 向 LLM 提供当前 Agent 可用的工具列表。
     * <p>
     * 从 {@link ToolRegistry} 中过滤出该 Agent 可用（含公共工具）的工具，
     * 并将 {@link ToolMetadata} 转换为 LangChain4j 的 {@link ToolSpecification}。
     *
     * @param request 工具提供请求（包含对话上下文、memoryId 等）
     * @return 工具提供结果，包含 ToolSpecification 列表
     */
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {

        List<ChatMessage> messages = request.messages();
        if (CollectionUtils.isEmpty(messages) || messages.get(messages.size() - 1) instanceof UserMessage) {
            return addToolsOnly();
        }

        toolRegistry.ensureInitialized();

        // only meta tools send to llm to reduce context.
        List<ToolMetadata> tools = availableTools();

        // 本轮需要下发的工具名（add_tools 显式声明 / 上一轮调用的工具）
        List<String> requiredNames = Lists.newArrayList();
        ChatMessage message = messages.get(messages.size() - 1);

        if (!(message instanceof ToolExecutionResultMessage)) {
            // 非工具结果消息（如普通助手回复），无需继续下发工具
            return addToolsOnly();
        }

        String toolName = ((ToolExecutionResultMessage) message).toolName();

        if (ADD_TOOLS_TOOL_NAME.equalsIgnoreCase(toolName)) {
            // 从上一轮的 add_tools 调用参数中取出需要添加的工具名
            ChatMessage preMessage = messages.get(messages.size() - 2);
            if (preMessage instanceof AiMessage) {
                for (ToolExecutionRequest req : ((AiMessage) preMessage).toolExecutionRequests()) {
                    if (!ADD_TOOLS_TOOL_NAME.equalsIgnoreCase(req.name())) {
                        continue;
                    }
                    requiredNames.addAll(parseToolNames(req.arguments()));
                }
            }
        } else {
            // 上一轮直接调用了某工具（可能未先 add_tools），把该工具续发下去，
            // 保证模型后续仍可正常调用，避免"只有一轮能用"。
            requiredNames.add(toolName);
        }

        if (CollectionUtils.isEmpty(requiredNames)) {
            // add_tools 未解析到任何工具名，只兜底下发 add_tools，避免模型彻底失去工具
            log.warn("provideTools: no tool name resolved from message, fallback to add_tools only, agent='{}'", agentCode);
            return addToolsOnly();
        }

        tools = tools.stream()
                .filter(tool -> requiredNames.stream()
                        .anyMatch(name -> StringUtils.equalsIgnoreCase(name, tool.getName())))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(tools)) {
            // 请求的工具不存在 / 不可用：兜底下发 add_tools，让模型能自我纠正而不是卡死
            log.warn("provideTools: requested tools {} not available for agent '{}', fallback to add_tools only",
                    requiredNames, agentCode);
            return addToolsOnly();
        }

        Map<String, List<ToolSpecification>> cache = Maps.newHashMap();
        Set<String> localCache = Sets.newHashSet();

        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        // 保持 add_tools 常驻，模型随时可以添加其它工具
        buildAddToolsTool(builder);

        Set<String> mpcCache = Sets.newHashSet();

        for (ToolMetadata tool : tools) {


            String sourceType = tool.getSourceType();


            if (Strings.CS.equals(sourceType, SourceType.LOCAL)) {

                if (localCache.contains(tool.getName())) {
                    continue;
                }

                String sourceRef = tool.getSourceRef();
                String beanName = sourceRef.split("\\.")[0];

                List<ToolSpecification> specs;
                if (cache.containsKey(beanName)) {
                    specs = cache.get(beanName);
                } else {
                    Object bean = applicationContext.getBean(beanName);
                    try {
                        specs = new ArrayList<>(ToolSpecifications.toolSpecificationsFrom(bean));
                    } catch (Exception e) {
                        // langchain4j 反射生成 spec 失败（例如 @Tool 方法含其无法识别的自定义
                        // ToolContext 参数，会把 ToolContext 当普通参数导致 spec 生成异常）
                        log.warn("toolSpecificationsFrom failed for bean '{}', fallback to stored schema for tool '{}'",
                                beanName, tool.getName(), e);
                        specs = new ArrayList<>();
                    }

                    // 兜底：langchain4j 反射失败或生成的 spec 中没有匹配当前工具名的 spec 时，
                    // 回退到注册时生成的干净 schema（只含 required 的 @P 参数），
                    // 确保 ask_user 等含自定义 ToolContext 参数的工具能被正确加载、走正常执行路径，
                    // 否则 LLM 调用会被 langchain4j 当作"幻觉工具"用空 context 执行。
                    boolean matched = specs.stream().anyMatch(s -> s.name().equals(tool.getName()));
                    if (!matched) {
                        JsonObjectSchema params = McpSchemaConverter.convert(tool.getParametersSchema());
                        if (params == null) {
                            params = JsonObjectSchema.builder().build();
                        }
                        specs.add(ToolSpecification.builder()
                                .name(tool.getName())
                                .description(StringUtils.defaultString(tool.getDescription()))
                                .parameters(params)
                                .build());
                        log.warn("No matching spec from langchain4j for tool '{}', fallback to stored schema", tool.getName());
                    }

                    cache.put(beanName, specs);
                }

                localCache.add(tool.getName());

                builder.addAll(specs.stream().filter(spec -> {
                    return spec.name().equals(tool.getName());
                }).map(spec -> AiServiceTool.builder()
                        .toolSpecification(spec)
                        .toolExecutor(this::execute)
                        .build()).collect(Collectors.toList()));
            }

            if (Strings.CS.equals(sourceType, SourceType.MCP)) {

                // 将 MCP 的 JSON Schema 转为 LangChain4j 的 JsonObjectSchema
                JsonObjectSchema parameters = McpSchemaConverter.convert(tool.getParametersSchema());
                if (parameters == null) {
                    parameters = JsonObjectSchema.builder().build();
                }

                if (mpcCache.contains(tool.getName())) {
                    continue;
                }

                ToolSpecification toolSpecification = ToolSpecification.builder()
                        .name(tool.getName())
                        .description(StringUtils.defaultString(tool.getDescription()))
                        .parameters(parameters)
                        .build();

                builder.add(AiServiceTool.builder()
                        .toolSpecification(toolSpecification)
                        .toolExecutor(this::execute)
                        .build());

                mpcCache.add(tool.getName());
            }
        }

        return builder.build();
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    /**
     * 执行工具调用 —— 由 LangChain4j 在 LLM 决定调用工具时触发。
     * <p>
     * 将 LC4j 的 {@link ToolExecutionRequest} 转换为内部的 {@link ToolCall}，
     * 构建 {@link ToolContext}，然后委托给 {@link ToolInvocationInterceptor} 执行。
     *
     * @param request 工具执行请求（工具名、参数、id）
     * @return 工具执行结果字符串
     */
    public String execute(ToolExecutionRequest request, Object memoryId) {
        ToolExecutionResult result = executeStructured(request, memoryId);

        if (result.isPending()) {
            log.warn("Tool '{}' returned PENDING status (requestId={}). " +
                            "Current implementation does not support blocking for confirmation; " +
                            "returning empty result to allow LLM to proceed.",
                    request.name(), result.getPendingRequestId());
        } else if (result.isFail()) {
            log.error("Tool '{}' execution failed: code={}, message={}",
                    request.name(), result.getErrorCode(), result.getErrorMessage());
        }

        // 内部全程结构化流转，这里是唯一转换为 String 交给框架的出口
        return result.toLlmText();
    }

    /**
     * 结构化执行工具调用（供桥接器与幻觉兜底复用，避免重复逻辑）。
     * <p>
     * 与 {@link #execute(ToolExecutionRequest, Object)} 的区别：本方法不转 String，
     * 返回结构化结果供上层按 errorCode 决定后续动作（引导 add_tools / 让模型修正参数）。
     *
     * @param request  工具执行请求
     * @param memoryId 记忆ID
     * @return 结构化执行结果
     */
    public ToolExecutionResult executeStructured(ToolExecutionRequest request, Object memoryId) {
        ToolCall toolCall = ToolUtils.toToolCall(request);
        ToolContext context = buildContext(request, memoryId);

        log.info("LC4j tool bridge: executing tool='{}', agent='{}'", request.name(), agentCode);

        ToolExecutionResult result;
        try {
            result = interceptor.execute(toolCall, context);
        } catch (Exception e) {
            log.error("Tool '{}' execution threw exception", request.name(), e);
            result = ToolExecutionResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR,
                    "工具执行异常: " + e.getMessage());
        }
        if (result == null) {
            result = ToolExecutionResult.fail(ToolExecutionResult.ERROR_UNKNOWN, "工具执行未返回结果");
        }
        return result;
    }



    /**
     * 仅下发 add_tools 元工具（兜底场景），保证模型始终有自我纠正的入口。
     */
    private ToolProviderResult addToolsOnly() {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        buildAddToolsTool(builder);
        return builder.build();
    }

    /**
     * 构建 add_tools 元工具：描述中带上当前真实可用的工具名称，
     * 从源头降低模型编造工具名的概率。
     */
    private void buildAddToolsTool(ToolProviderResult.Builder builder) {
        List<String> availableNames = availableToolNames();
        String description = CollectionUtils.isEmpty(availableNames)
                ? "添加工具到工具列表。当前没有可用工具，禁止编造工具名称。"
                : "添加工具到工具列表。只能添加下列已存在的工具（禁止编造工具名称）：\n"
                + String.join(", ", availableNames);

        ToolSpecification spec = ToolSpecification.builder()
                .name(ADD_TOOLS_TOOL_NAME)
                .description(description)
                .parameters(JsonObjectSchema.builder()
                        .addProperty(TOOL_ARGUMENT, JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .description("需要添加的工具名称，必须是已存在的工具")
                                .build())
                        .required(TOOL_ARGUMENT)
                        .build())
                .build();

        builder.add(AiServiceTool.builder()
                .toolSpecification(spec)
                .toolExecutor(this::execute)
                .build());
    }

    /**
     * 解析 add_tools 参数中的工具名称列表。
     */
    private List<String> parseToolNames(String arguments) {
        if (StringUtils.isBlank(arguments)) {
            return Lists.newArrayList();
        }
        try {
            HashMap map = JsonUtils.toObj(arguments, HashMap.class);
            if (MapUtils.isEmpty(map) || !map.containsKey(TOOL_ARGUMENT)) {
                return Lists.newArrayList();
            }
            Object obj = map.get(TOOL_ARGUMENT);
            if (obj instanceof Collection<?> collection) {
                List<String> names = Lists.newArrayList();
                for (Object item : collection) {
                    if (item != null && StringUtils.isNotBlank(item.toString())) {
                        names.add(item.toString().trim());
                    }
                }
                return names;
            }
            if (obj != null && StringUtils.isNotBlank(obj.toString())) {
                // 兼容模型传入逗号/空白分隔的字符串
                return Arrays.stream(obj.toString().split("[,，\\s]+"))
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to parse tool names from arguments: {}", arguments, e);
        }
        return Lists.newArrayList();
    }

    /**
     * 当前 Agent 可用的工具（已按 MCP 范围 / 启用状态过滤）。
     */
    private List<ToolMetadata> availableTools() {
        toolRegistry.ensureInitialized();
        List<ToolMetadata> tools = toolRegistry.getToolsForAgent(agentCode);
        if (CollectionUtils.isEmpty(tools)) {
            return Lists.newArrayList();
        }
        // mcp改由前端传入 (全部传给大模型耗费token）
        return tools.stream().filter(this::isToolAvailable).collect(Collectors.toList());
    }

    /**
     * 当前 Agent 可用工具的名称列表。
     */
    private List<String> availableToolNames() {
        return availableTools().stream()
                .map(ToolMetadata::getName)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 工具是否对当前 Agent 可见：LOCAL 工具始终可见，MCP 工具需命中传入的 mcp_code 且启用。
     */
    private boolean isToolAvailable(ToolMetadata tool) {
        String sourceType = tool.getSourceType();

        // 未指定 mcpCodes 时过滤掉所有 MCP 工具
        if (CollectionUtils.isEmpty(mcpCodes)) {
            return !Objects.equals(sourceType, SourceType.MCP);
        }

        // sourceType为MCP，且sourceRef的第一个字符串为mcp_code
        boolean mcpMatched = Strings.CS.equals(sourceType, SourceType.MCP)
                && StringUtils.isNotBlank(tool.getSourceRef())
                && mcpCodes.contains(tool.getSourceRef().split(":")[0])
                && Objects.equals(tool.getStatus(), "ENABLED");
        return mcpMatched || Strings.CS.equals(sourceType, SourceType.LOCAL);
    }

    /**
     * 构建工具执行上下文。
     */
    private ToolContext buildContext(ToolExecutionRequest request, Object memoryId) {
        ToolContext.ToolContextBuilder builder = ToolContext.builder()
                .agentCode(agentCode)
                .userCode(userCode)
                .conversationCode(conversationCode)
                .traceId(generateTraceId(request));

        // 如果 memoryId 可转为字符串，尝试解析会话编码
        if (memoryId != null && StringUtils.isBlank(conversationCode)) {
            String memIdStr = memoryId.toString();
            builder.conversationCode(memIdStr);
        }

        return builder.build();
    }

    /**
     * 生成 TraceId，优先使用 ToolExecutionRequest.id。
     */
    private String generateTraceId(ToolExecutionRequest request) {
        if (request.id() != null && !request.id().isBlank()) {
            return "tool_" + request.id().replace("-", "").substring(0, Math.min(16, request.id().length()));
        }
        return "tool_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== 便捷工厂方法 ====================

    /**
     * 为指定 Agent 创建桥接器（快捷方法）。
     *
     * @param toolRegistry 工具注册中心
     * @param interceptor  调用拦截器
     * @param agentCode    Agent 编码
     * @return 桥接器实例
     */
    public static LangChain4JToolBridge forAgent(ToolRegistry toolRegistry,
                                                 ToolInvocationInterceptor interceptor,
                                                 ApplicationContext applicationContext,
                                                 String agentCode,
                                                 String userCode,
                                                 String conversationCode, List<String> mcpCodes) {
        return LangChain4JToolBridge.builder()
                .toolRegistry(toolRegistry)
                .interceptor(interceptor)
                .applicationContext(applicationContext)
                .agentCode(agentCode)
                .userCode(userCode)
                .conversationCode(conversationCode)
                .mcpCodes(mcpCodes)
                .build();
    }

    /**
     * 创建公共工具桥接器（不限定 Agent，所有工具均可使用）。
     *
     * @param toolRegistry 工具注册中心
     * @param interceptor  调用拦截器
     * @return 桥接器实例
     */
    public static LangChain4JToolBridge forAll(ToolRegistry toolRegistry,
                                               ToolInvocationInterceptor interceptor, ApplicationContext applicationContext, List<String> mcpCodes) {
        return LangChain4JToolBridge.builder()
                .toolRegistry(toolRegistry)
                .interceptor(interceptor)
                .applicationContext(applicationContext)
                .mcpCodes(mcpCodes)
                .build();
    }

}