package com.xiaomizhou.dpsk.tool;

import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具调用拦截器。
 * <p>
 * 在 LangChain4j 的 ToolProvider 基础上封装一层，拦截所有工具调用：
 * 1. 检查权限，若需确认则挂起（当前版本仅打 warn 日志）
 * 2. 动态补全参数
 * 3. 路由并执行
 * 4. 审计日志
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

    /**
     * 拦截并执行工具调用。
     *
     * @param call    工具调用请求
     * @param context 执行上下文
     * @return 执行结果
     */
    public ToolExecutionResult execute(ToolCall call, ToolContext context) {
        long startTime = System.currentTimeMillis();

        // 1. 查找工具元数据
        ToolMetadata metadata = registry.getMetadata(call.getName());
        if (metadata == null) {
            log.warn("Tool not found: {}", call.getName());
            return ToolExecutionResult.fail("Tool not found: " + call.getName());
        }

        if (!metadata.isEnabled()) {
            log.warn("Tool is disabled: {}", call.getName());
            return ToolExecutionResult.fail("Tool is disabled: " + call.getName());
        }

        // 2. 检查权限
        if (metadata.isDangerous() && !call.isConfirmed()) {
            log.warn("⚠ DANGEROUS tool '{}' (riskLevel={}) called but not confirmed. " +
                            "Proceeding with execution but this should require user confirmation in production.",
                    call.getName(), metadata.getRiskLevel());

            // 生成确认请求（当前不阻塞，仅记录日志）
            confirmationManager.requestConfirmation(call, metadata);
            // 注意：当前版本危险操作不阻塞，直接继续执行。后续版本在此 return ToolExecutionResult.pending(requestId)
        }

        // 3. 动态补全参数
        enrichParameters(call, context, metadata);

        // 4. 路由并执行
        String result;
        String status;
        String errorMessage = null;
        try {
            result = registry.getExecutorRouter().execute(metadata, call, context);
            status = ToolExecutionResult.STATUS_SUCCESS;
            log.debug("Tool '{}' executed successfully in {}ms", 
                    call.getName(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Tool '{}' execution failed", call.getName(), e);
            result = null;
            status = ToolExecutionResult.STATUS_FAIL;
            errorMessage = e.getMessage();
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;

        // 5. 审计日志（异步写入，不阻塞）
        auditLogger.log(call, metadata, context, result, status, executionTimeMs, errorMessage);

        if (ToolExecutionResult.STATUS_SUCCESS.equals(status)) {
            return ToolExecutionResult.success(result, executionTimeMs);
        } else {
            return ToolExecutionResult.fail(errorMessage);
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
