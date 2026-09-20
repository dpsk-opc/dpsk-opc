package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.model.ToolResult;
import com.xiaomizhou.dpsk.tool.workspace.PathAccessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * 工具调用拦截器。
 * <p>
 * 在 LangChain4j 的 ToolProvider 基础上封装一层，拦截所有工具调用：
 * 1. 检查工具是否存在、是否启用、是否属于当前 Agent 可用范围
 * 2. 检查权限，若需确认则挂起（当前版本仅打 warn 日志）
 * 3. 动态补全参数、替换敏感参数
 * 4. 路由并执行（结果保持结构化）
 * 5. 审计日志
 * <p>
 * 所有失败都带 <b>错误码</b>，方便上层区分「工具不存在（引导 add_tools）」
 * 与「参数/执行错误（引导模型修正）」，不再用异常字符串兜底。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
@RequiredArgsConstructor
public class ToolInvocationInterceptor {

    private final ToolRegistry registry;

    private final ToolAuditLogger auditLogger;

    private final ToolConfirmationManager confirmationManager;

    private final PrivateParameterReplacer privateParameterReplacer;

    /**
     * 路径访问校验服务（工作空间边界）。
     * <p>
     * 可为 null（未启用工作空间能力时）。为空时跳过边界校验，保持存量行为。
     */
    private final PathAccessValidator pathAccessValidator;

    /**
     * 拦截并执行工具调用。
     *
     * @param call    工具调用请求
     * @param context 执行上下文
     * @return 执行结果（结构化，带 errorCode）
     */
    public ToolExecutionResult execute(ToolCall call, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String toolName = call.getName();

        // 0. 元工具豁免：add_tools 由桥接器内联处理，不注册在工具表中，
        //    也不涉及文件系统访问（不参与工作空间校验）。
        //    正常流程不会走到这里；若走到说明调用方绕过了桥接器，给出明确提示而非"工具不存在"。
        if (LangChain4JToolBridge.ADD_TOOLS_TOOL_NAME.equalsIgnoreCase(toolName)) {
            log.warn("Meta tool '{}' should be handled by LangChain4JToolBridge, got interceptor call", toolName);
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_TOOL_NOT_FOUND,
                    "add_tools 是内置元工具，无需添加即可直接使用");
        }

        // 1. 查找工具元数据（不存在则带 TOOL_NOT_FOUND，供上层引导 add_tools）
        ToolMetadata metadata = registry.getMetadata(toolName);
        if (metadata == null) {
            log.warn("Tool not found in registry: {}", toolName);
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_TOOL_NOT_FOUND,
                    "工具不存在: " + toolName);
        }

        // 2. 启用状态校验
        if (!metadata.isEnabled()) {
            log.warn("Tool is disabled: {}", toolName);
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_TOOL_DISABLED,
                    "工具已被禁用: " + toolName);
        }

        // 2.1 Agent 作用域校验：防止 LLM 直接调用不属于当前 Agent 的工具
        if (StringUtils.isNotBlank(context.getAgentCode())
                && !isInAgentScope(metadata, context.getAgentCode())) {
            log.warn("Tool '{}' is not in agent '{}' scope", toolName, context.getAgentCode());
            return ToolExecutionResult.fail(ToolExecutionResult.ERROR_TOOL_NOT_IN_AGENT_SCOPE,
                    "工具 " + toolName + " 不属于当前 Agent 的可用范围");
        }

        // 3. 动态补全参数
        enrichParameters(call, context, metadata);

        // 3.1 工作空间边界校验（能力位 → 路径获取 → 判定 → 确认）
        if (pathAccessValidator != null) {
            ToolExecutionResult pathResult;
            try {
                pathResult = pathAccessValidator.validate(metadata, context, call.getParameters());
            } catch (Exception e) {
                // 校验器自身异常不能放行，保守拒绝
                log.error("Path access validation failed unexpectedly, deny tool '{}'", toolName, e);
                pathResult = ToolExecutionResult.fail(ToolExecutionResult.ERROR_PATH_OUT_OF_WORKSPACE,
                        "路径校验失败，已拒绝本次文件访问: " + e.getMessage());
            }
            if (pathResult != null) {
                long cost = System.currentTimeMillis() - startTime;
                auditLogger.log(call, metadata, context,
                        ToolResult.builder()
                                .success(false)
                                .errorCode(pathResult.getErrorCode())
                                .errorMessage(pathResult.getErrorMessage())
                                .build());
                log.warn("Tool '{}' blocked by workspace boundary: code={}", toolName, pathResult.getErrorCode());
                return pathResult;
            }
        }

        // 4. 替换敏感参数
        ToolCall newCall = privateParameterReplacer.replace(call);

        // 5. 路由并执行（结果结构化）
        ToolResult toolResult;
        try {
            toolResult = registry.getExecutorRouter().execute(metadata, newCall, context);
            if (toolResult == null) {
                toolResult = ToolResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR, "工具执行器未返回结果");
            }
        } catch (Exception e) {
            log.error("Tool '{}' execution failed", toolName, e);
            toolResult = ToolResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR,
                    "工具执行异常: " + e.getMessage(), e);
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        toolResult.withExecutionTime(executionTimeMs);

        // 6. 审计日志（内部自动兜底，不阻塞）
        auditLogger.log(newCall, metadata, context, toolResult);

        return toExecutionResult(toolResult, executionTimeMs);
    }

    /**
     * 结构化执行结果 → 上层结果对象。
     */
    private ToolExecutionResult toExecutionResult(ToolResult toolResult, long executionTimeMs) {
        if (toolResult.isSuccess()) {
            return ToolExecutionResult.builder()
                    .status(ToolExecutionResult.STATUS_SUCCESS)
                    .payload(toolResult.getData())
                    .result(resolveText(toolResult))
                    .executionTimeMs(executionTimeMs)
                    .build();
        }
        return ToolExecutionResult.builder()
                .status(ToolExecutionResult.STATUS_FAIL)
                .errorCode(Optional.ofNullable(toolResult.getErrorCode())
                        .orElse(ToolExecutionResult.ERROR_UNKNOWN))
                .errorMessage(StringUtils.defaultIfBlank(toolResult.getErrorMessage(), "工具执行失败"))
                .executionTimeMs(executionTimeMs)
                .build();
    }

    private String resolveText(ToolResult toolResult) {
        if (StringUtils.isNotBlank(toolResult.getText())) {
            return toolResult.getText();
        }
        Object data = toolResult.getData();
        if (data == null || data instanceof String) {
            return (String) data;
        }
        return null;
    }

    /**
     * 判断工具是否属于该 Agent 的可用范围（Agent 绑定工具 + 内置工具）。
     */
    private boolean isInAgentScope(ToolMetadata metadata, String agentCode) {
        try {
            return registry.getToolsForAgent(agentCode).stream()
                    .anyMatch(t -> t.getCode() != null && t.getCode().equals(metadata.getCode()));
        } catch (Exception e) {
            log.warn("Check agent scope failed for tool '{}', agent '{}', fallback to allow",
                    metadata.getName(), agentCode, e);
            return true;
        }
    }

    /**
     * 动态补全参数：根据参数 Schema 中的 x-context-mapping 扩展属性注入上下文值。
     * <p>
     * 也支持在工具方法上使用 @InjectContext 注解自动填充（后续扩展）。
     */
    private void enrichParameters(ToolCall call, ToolContext context, ToolMetadata metadata) {
        // 自动注入 Agent 编码
        if (context.getAgentCode() != null && !call.getParameters().containsKey("agentCode")) {
            call.getParameters().put("agentCode", context.getAgentCode());
        }

        // 自动注入用户编码
        if (context.getUserCode() != null && !call.getParameters().containsKey("userCode")) {
            call.getParameters().put("userCode", context.getUserCode());
        }

        // 自动注入会话编码
        if (context.getConversationCode() != null && !call.getParameters().containsKey("conversationCode")) {
            call.getParameters().put("conversationCode", context.getConversationCode());
        }

        // 自动注入 TraceId
        if (context.getTraceId() != null && !call.getParameters().containsKey("traceId")) {
            call.getParameters().put("traceId", context.getTraceId());
        }
    }
}
