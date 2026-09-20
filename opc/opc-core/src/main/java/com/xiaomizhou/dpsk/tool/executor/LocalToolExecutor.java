package com.xiaomizhou.dpsk.tool.executor;

import com.xiaomizhou.dpsk.tool.ToolExecutor;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolExecutionResult;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 本地工具执行器，通过反射调用 Spring Bean 上标注了 @Tool 注解的方法。
 * <p>
 * 不再把执行异常拼成字符串当作"成功结果"返回，而是返回结构化的 {@link ToolResult}，
 * 让上层能区分「工具不存在」与「执行报错」。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class LocalToolExecutor implements ToolExecutor {

    private final ApplicationContext applicationContext;

    /** 本地工具调用超时时间（秒），默认 120 秒 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    public LocalToolExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public ToolResult execute(ToolCall call, ToolContext context, ToolMetadata metadata) {
        String sourceRef = resolveSourceRef(call);

        if (sourceRef == null || !sourceRef.contains(".")) {
            return ToolResult.fail(ToolExecutionResult.ERROR_BEAN_OR_METHOD_NOT_FOUND,
                    "工具 sourceRef 配置非法，期望 beanName.methodName，实际: " + sourceRef);
        }

        String[] parts = sourceRef.split("\\.", 2);
        String beanName = parts[0];
        String methodName = parts[1];

        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (BeansException e) {
            log.error("Bean not found: {}", beanName, e);
            return ToolResult.fail(ToolExecutionResult.ERROR_BEAN_OR_METHOD_NOT_FOUND,
                    "工具实现 Bean 不存在: " + beanName, e);
        }

        Method method = findMethod(bean.getClass(), methodName, call.getParameters());
        if (method == null) {
            log.error("Method not found: {} on bean {}", methodName, beanName);
            return ToolResult.fail(ToolExecutionResult.ERROR_BEAN_OR_METHOD_NOT_FOUND,
                    "工具实现方法不存在: " + beanName + "." + methodName);
        }

        log.debug("Invoking local tool: {}.{} with params: {}", beanName, methodName, call.getParameters());

        Object[] args = resolveArgs(method, call.getParameters(), context);

        Object result;
        try {
            result = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return method.invoke(bean, args);
                        } catch (InvocationTargetException e) {
                            throw new ToolInvocationException(e.getTargetException());
                        } catch (Exception e) {
                            throw new ToolInvocationException(e);
                        }
                    }).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Local tool timeout: {}.{} (>{}s)", beanName, methodName, DEFAULT_TIMEOUT_SECONDS);
            return ToolResult.fail(ToolExecutionResult.ERROR_TIMEOUT,
                    "工具执行超时（>" + DEFAULT_TIMEOUT_SECONDS + " 秒）: " + beanName + "." + methodName);
        } catch (IllegalArgumentException e) {
            log.error("Invoke local tool failed: {}.{}", beanName, methodName, e);
            return ToolResult.fail(ToolExecutionResult.ERROR_PARAM_INVALID,
                    "工具参数不合法: " + beanName + "." + methodName, e);
        } catch (Exception e) {
            log.error("Invoke local tool failed: {}.{}", beanName, methodName, e);
            return ToolResult.fail(ToolExecutionResult.ERROR_EXECUTION_ERROR,
                    "工具执行失败: " + beanName + "." + methodName, e);
        }

        if (result == null) {
            return ToolResult.builder()
                    .success(true)
                    .data(null)
                    .text("工具执行成功，但返回结果为 null")
                    .build();
        }

        log.debug("Invoke local tool success: result: {}", result);
        return ToolResult.builder()
                .success(true)
                .data(result)
                .text(result instanceof String s ? s : null)
                .build();
    }

    /**
     * 执行器内部异常包装，用于把业务异常从 CompletableFuture 中带出来。
     */
    private static class ToolInvocationException extends RuntimeException {
        ToolInvocationException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 从 ToolCall 中解析 sourceRef。如果 call 中有 sourceRef 则用，否则需要外部注入。
     */
    private String resolveSourceRef(ToolCall call) {
        // sourceRef 由 Router 层在调用前注入到 extra 参数中
        if (call.getParameters() != null) {
            Object ref = call.getParameters().get("__sourceRef__");
            if (ref != null) {
                return ref.toString();
            }
        }
        return null;
    }

    /**
     * 查找匹配的方法。
     */
    private Method findMethod(Class<?> clazz, String methodName, Map<String, Object> params) {
        return Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析方法参数（按参数名匹配）。支持简单类型自动注入 context。
     */
    private Object[] resolveArgs(Method method, Map<String, Object> params, ToolContext context) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();

            // 按照类系注入优先级更高
            if (ToolContext.class.isAssignableFrom(parameters[i].getType())) {
                // 自动注入 ToolContext
                args[i] = context;
            } else if (params != null && params.containsKey(paramName)) {
                args[i] = params.get(paramName);
            } else {
                args[i] = null;
            }
        }
        return args;
    }
}
