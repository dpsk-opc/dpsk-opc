package com.xiaomizhou.dpsk.tool;

import com.google.common.base.Joiner;
import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.tool.repository.ToolRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.community.tool.webscraper.WebScraperTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具自动注册处理器。
 * <p>
 * 启动时扫描所有 Spring Bean 中标注了 @Tool 注解的方法，自动注册到 DB 和 ToolRegistry。
 * 如果 t_tool 中已存在同名工具，则更新描述和参数。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Slf4j
public class ToolAutoRegistrar {

    private final ApplicationContext applicationContext;
    private final ToolRepository toolRepository;
    private final ToolRegistry toolRegistry;

    public ToolAutoRegistrar(ApplicationContext applicationContext,
                             ToolRepository toolRepository,
                             ToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRepository = toolRepository;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 扫描所有 Bean 中的 @Tool 方法并注册。
     */
    public void scanAndRegister() {
        log.info("Scanning for @Tool annotated methods...");
        int registeredCount = 0;

        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class);
        // 也扫描 @Service、@Repository 等
        beans.putAll(applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Service.class));
        beans.putAll(applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Repository.class));
        beans.putAll(applicationContext.getBeansWithAnnotation(ToolMeta.class));

        WebScraperTool scraperTool = applicationContext.getBean(WebScraperTool.class);
        beans.put("webScraperTool",scraperTool);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();
            Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

            List<Method> toolMethods = findToolMethods(targetClass);
            for (Method method : toolMethods) {
                try {
                    registerTool(beanName, targetClass, method);
                    registeredCount++;
                } catch (Exception e) {
                    log.warn("Failed to register tool: {}.{}", beanName, method.getName(), e);
                }
            }
        }

        log.info("Tool auto-registration complete: {} tools registered", registeredCount);
    }

    private List<Method> findToolMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class)
                        || m.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
                .toList();
    }

    private void registerTool(String beanName, Class<?> clazz, Method method) {
        // 优先使用 langchain4j 的 @Tool，fallback 到 spring-ai 的 @Tool
        Tool lc4jTool = method.getAnnotation(Tool.class);
//        org.springframework.ai.tool.annotation.Tool saiTool = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);

        String toolName = lc4jTool != null ? lc4jTool.name() : "";
        String description = lc4jTool != null ? String.join(", ", lc4jTool.value()) : "";
        String sourceRef = beanName + "." + method.getName();
        String code = "local_" + sourceRef.replace('.', '_');

        ToolMeta meta = clazz.getAnnotation(ToolMeta.class);

        if (toolName == null || toolName.isEmpty()) {
            toolName = method.getName();
        }

        // 生成简单的参数 JSON Schema
        String parametersSchema = generateParametersSchema(method);

        // 检查 DB 中是否已存在
        ToolMetadata existing = toolRepository.findByCode(code);
        if (existing != null) {
            // 更新描述和参数
            existing.setName(toolName);
            existing.setDescription(description);
            existing.setParametersSchema(parametersSchema);
            existing.setSourceRef(sourceRef);
            toolRepository.save(existing);
            log.debug("Updated tool: {} (code={})", toolName, code);
        } else {
            // 新增
            ToolMetadata metadata = ToolMetadata.builder()
                    .code(code)
                    .name(toolName)
                    .description(description)
                    .parametersSchema(parametersSchema)
                    .sourceType(SourceType.LOCAL)
//                    .sourceRef(clazz.getCanonicalName() + "." + method.getName())
                    .sourceRef(sourceRef)
                    .riskLevel(ToolMetadata.RISK_NORMAL)
                    .status(ToolMetadata.STATUS_ENABLED)
                    .category(Objects.isNull(meta) ? "" : meta.category())
                    .tags(Objects.isNull(meta) || ArrayUtils.isEmpty(meta.tags()) ? "" : Joiner.on(",").join(meta.tags()))
                    .cacheable(Objects.nonNull(meta) && meta.cacheable())
                    .timeoutMs(Objects.isNull(meta) ? 0 : (int) meta.timeout())
                    .ownerAgentCode("")
                    .build();
            toolRepository.save(metadata);
            log.info("Registered new tool: {} (code={})", toolName, code);
        }

        // 同步到内存注册中心
        ToolMetadata memMeta = toolRepository.findByCode(code);
        if (memMeta != null) {
            toolRegistry.register(memMeta);
        }
    }

    /**
     * 根据方法签名生成简单的参数 JSON Schema。
     */
    private String generateParametersSchema(Method method) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }

        StringBuilder sb = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) sb.append(",");
            String paramName = parameters[i].getName();
            String paramType = mapJavaTypeToJsonType(parameters[i].getType());

            // 尝试获取 @ToolParam 注解的描述
            String paramDesc = paramName;
            P toolParam =
                    parameters[i].getAnnotation(P.class);
            dev.langchain4j.agent.tool.ToolMemoryId lc4jMemId =
                    parameters[i].getAnnotation(dev.langchain4j.agent.tool.ToolMemoryId.class);

            if (toolParam != null && !toolParam.description().isEmpty()) {
                paramDesc = toolParam.description();
            }
            boolean required = !Objects.isNull(toolParam);
            if (Objects.nonNull(toolParam)) {
                required = toolParam.required();
            }



            sb.append(String.format("\"%s\":{\"type\":\"%s\",\"description\":\"%s\",\"required\":%s}",
                    paramName, paramType, paramDesc,required));
        }
        sb.append("}}");
        return sb.toString();
    }

    private String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) return "integer";
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) return "number";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        return "string";
    }
}
