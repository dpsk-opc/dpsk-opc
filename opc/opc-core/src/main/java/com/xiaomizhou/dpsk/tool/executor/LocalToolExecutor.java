package com.xiaomizhou.dpsk.tool.executor;

import com.xiaomizhou.dpsk.tool.ToolExecutor;
import com.xiaomizhou.dpsk.tool.model.ToolCall;
import com.xiaomizhou.dpsk.tool.model.ToolContext;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 本地工具执行器，通过反射调用 Spring Bean 上标注了 @Tool 注解的方法。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class LocalToolExecutor implements ToolExecutor {

    private final ApplicationContext applicationContext;

    /** 本地工具调用超时时间（秒），默认 30 秒 */
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    public LocalToolExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public String execute(ToolCall call, ToolContext context,ToolMetadata metadata) throws Exception {
        String sourceRef = resolveSourceRef(call);

        if (sourceRef == null || !sourceRef.contains(".")) {
            throw new IllegalArgumentException("Invalid sourceRef format, expected beanName.methodName: " + sourceRef);
        }

        String[] parts = sourceRef.split("\\.", 2);
        String beanName = parts[0];
        String methodName = parts[1];

        Object bean = applicationContext.getBean(beanName);
        if (bean == null) {
            throw new IllegalStateException("Bean not found: " + beanName);
        }

        Method method = findMethod(bean.getClass(), methodName, call.getParameters());
        if (method == null) {
            throw new NoSuchMethodException("Method not found: " + methodName + " on bean " + beanName);
        }


        log.debug("Invoking local tool: {}.{} with params: {}", beanName, methodName, call.getParameters());

        // 简单参数调用（参数按方法参数顺序传入）
        Object[] args = resolveArgs(method, call.getParameters(), context);

        Object result = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return method.invoke(bean, args);
                    } catch (Exception e) {
                        log.error("Invoke local tool failed: {}.{}", beanName, methodName, e);
                        return "工具执行失败!" + e.getMessage();
                    }
                }).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (result == null) {
            return "";
        }
        if (result instanceof String s) {
            return s;
        }
        return result.toString();
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
        java.lang.reflect.Parameter[] parameters = method.getParameters();
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
