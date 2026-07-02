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
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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


    public static class AddTools {

        @Tool(name = ADD_TOOLS_TOOL_NAME, value = "添加工具到工具列表", returnBehavior = ReturnBehavior.IMMEDIATE)
        public String addTools(@P(name = TOOL_ARGUMENT, required = true) List<String> toolNames) {
            return "成功添加工具到工具列表";
        }

    }

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
            return ToolProviderResult.builder()
                    .addAll(ToolSpecifications.toolSpecificationsFrom(AddTools.class).stream().map(spec -> {
                        return AiServiceTool.builder()
                                .toolSpecification(spec)
                                .toolExecutor(this::execute)
                                .build();

                    }).toList())
                    .build();
        }

        toolRegistry.ensureInitialized();

        // only meta tools send to llm to reduce context.
        List<ToolMetadata> tools = toolRegistry.getToolsForAgent(agentCode);


        // mcp工具

        // mcp改由前端传入 (全部传给大模型耗费token）
        tools = tools.stream().filter(tool -> {

            // 过滤掉MCP工具
            if (CollectionUtils.isEmpty(mcpCodes)) {
                return !Objects.equals(tool.getSourceType(), SourceType.MCP);
            }

            // sourceType为MCP，且sourceRef的第一个字符串为mcp_code
            return mcpCodes.contains(tool.getSourceRef().split(":")[0]) && Objects.equals(tool.getStatus(), "ENABLED") || Strings.CS.equals(tool.getSourceType(), SourceType.LOCAL);
        }).collect(Collectors.toList());

        ChatMessage message = messages.get(messages.size() - 1);

        if(!(message instanceof ToolExecutionResultMessage)){
            return ToolProviderResult.builder().build();
        }


        // add
        String toolName = ((ToolExecutionResultMessage) message).toolName();

        // get tool param.
        if (ADD_TOOLS_TOOL_NAME.equalsIgnoreCase(toolName)) {
            ChatMessage preMessage = messages.get(messages.size() - 2);
            if (preMessage instanceof AiMessage) {
                List<ToolExecutionRequest> requests = ((AiMessage) preMessage).toolExecutionRequests();
                for (ToolExecutionRequest req : requests) {
                    HashMap map = JsonUtils.toObj(req.arguments(), HashMap.class);
                    if (MapUtils.isNotEmpty(map) && map.containsKey(TOOL_ARGUMENT)) {
                        Object obj = map.get(TOOL_ARGUMENT);
                        if (obj instanceof List) {
                            List<String> toolNames = (List<String>) obj;
                            tools = tools.stream().filter(tool -> toolNames.contains(tool.getName())).collect(Collectors.toList());
                        }
                    }

                }
            }
        } else {
            tools = tools.stream().filter(tool -> tool.getName().equalsIgnoreCase(toolName)).toList();
        }
        if (CollectionUtils.isEmpty(tools)) {
            log.debug("No tools available for agent '{}'", agentCode);
            return ToolProviderResult.builder().build();
        }

        Map<String, List<ToolSpecification>> cache = Maps.newHashMap();
        Set<String> localCache = Sets.newHashSet();
        ToolProviderResult.Builder builder = ToolProviderResult.builder();

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
                    specs = ToolSpecifications.toolSpecificationsFrom(bean);

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
        ToolCall toolCall = ToolUtils.toToolCall(request);
        ToolContext context = buildContext(request, memoryId);

        log.info("LC4j tool bridge: executing tool='{}', agent='{}'",
                request.name(), agentCode/*, memoryId*/);

        ToolExecutionResult result = interceptor.execute(toolCall, context);

        if (ToolExecutionResult.STATUS_PENDING.equals(result.getStatus())) {
            log.warn("Tool '{}' returned PENDING status (requestId={}). " +
                            "Current implementation does not support blocking for confirmation; " +
                            "returning empty result to allow LLM to proceed.",
                    request.name(), result.getPendingRequestId());
            return "[Tool requires confirmation, requestId=" + result.getPendingRequestId() + "]";
        }

        if (ToolExecutionResult.STATUS_FAIL.equals(result.getStatus())) {
            log.error("Tool '{}' execution failed: {}", request.name(), result.getErrorMessage());
            return "Tool execution failed: " + result.getErrorMessage();
        }

        return result.getResult() != null ? result.getResult() : "";
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