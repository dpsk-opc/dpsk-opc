package com.xiaomizhou.dpsk.tool.workspace;

import com.xiaomizhou.dpsk.tool.model.ToolMetadata;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模型路径抽取器（对应 D12~D14、D17）。
 * <p>
 * <b>只做事实抽取，不做决策</b>：从工具调用参数中抽取"要访问哪些路径、方向是什么"，
 * 是否放行完全由 {@link PathGuard} 决定。原因：模型若同时是"被约束方"和"裁判"，
 * 在越权场景（幻觉、提示注入）下防护即失效。
 * <p>
 * <b>防注入要求（强制）</b>：
 * <ul>
 *   <li>输入仅含工具名 + 工具描述 + 完整参数 JSON，<b>绝不包含</b>外部文件内容、历史消息等不可信内容；</li>
 *   <li>抽取结果只作为候选路径，必须过 PathGuard；模型无法输出"已授权"；</li>
 *   <li>抽取失败 / 超时 / 格式非法 / 结果为空 → 一律降级为"需要用户确认"，不静默放行。</li>
 * </ul>
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Slf4j
public class PathExtractor {

    private final PathExtractionLlm llm;

    /**
     * 是否启用抽取。关闭时未声明工具直接降级为需要确认。
     */
    private final boolean enabled;

    public PathExtractor(PathExtractionLlm llm, boolean enabled) {
        this.llm = llm;
        this.enabled = enabled;
    }

    public static PathExtractor disabled() {
        return new PathExtractor(null, false);
    }

    /**
     * 抽取路径访问事实。
     *
     * @param toolName    工具名
     * @param description 工具描述
     * @param parameters  调用参数（完整 JSON 对象）
     * @return 抽取结果；{@code uncertain=true} 表示需要降级为确认
     */
    public ExtractionResult extract(String toolName, String description, Map<String, Object> parameters) {
        if (!enabled || llm == null) {
            return ExtractionResult.uncertain("路径抽取未启用");
        }
        if (parameters == null || parameters.isEmpty()) {
            return ExtractionResult.uncertain("无参数，无法确认是否涉及文件访问");
        }

        String prompt = buildPrompt(toolName, description, parameters);
        String content;
        try {
            content = llm.chat(prompt);
        } catch (Exception e) {
            log.warn("Path extraction llm call failed, tool={}", toolName, e);
            return ExtractionResult.uncertain("路径抽取调用失败");
        }

        if (StringUtils.isBlank(content)) {
            return ExtractionResult.uncertain("路径抽取返回为空");
        }

        return parse(content, parameters);
    }

    /**
     * 构建抽取提示词。
     * <p>
     * 输入严格限定为工具元信息 + 参数，不含任何外部内容。
     */
    private String buildPrompt(String toolName, String description, Map<String, Object> parameters) {
        String paramsJson;
        try {
            paramsJson = JsonUtils.toJson(parameters);
        } catch (Exception e) {
            paramsJson = String.valueOf(parameters);
        }
        if (paramsJson != null && paramsJson.length() > 4000) {
            paramsJson = paramsJson.substring(0, 4000);
        }

        return """
                你是工具参数的路径抽取器。你的任务只是从参数中【识别出文件系统路径】，不要做任何安全判断。

                工具名：%s
                工具描述：%s
                调用参数：%s

                要求：
                1. 只提取"值是文件系统路径"的参数（如目录、文件、通配路径），普通文本/ID/URL 不算；
                2. 方向判断：读取/查看/列出 → READ；写入/保存/创建/修改/删除 → WRITE；删除 → DELETE；
                3. 若无法确定是否为路径、或无法判断方向，不要猜测，把 uncertain 设为 true；
                4. 不确定参数是否为路径时，不要输出它。

                请仅输出 JSON：
                {"paths":[{"param":"参数名","path":"路径值","direction":"READ|WRITE|DELETE"}],"uncertain":false}
                若完全无法判断，输出：{"paths":[],"uncertain":true}
                """.formatted(toolName, StringUtils.defaultString(description), paramsJson);
    }

    /**
     * 解析抽取结果。
     */
    private ExtractionResult parse(String content, Map<String, Object> parameters) {
        String cleaned = content.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        ExtractionJson json;
        try {
            json = JsonUtils.toObj(cleaned, ExtractionJson.class);
        } catch (Exception e) {
            log.warn("Failed to parse path extraction result: {}", content, e);
            return ExtractionResult.uncertain("路径抽取结果格式非法");
        }
        if (json == null) {
            return ExtractionResult.uncertain("路径抽取结果解析失败");
        }

        List<PathAccess> accesses = new ArrayList<>();
        if (json.getPaths() != null) {
            for (PathItem item : json.getPaths()) {
                if (item == null || StringUtils.isBlank(item.getPath())) {
                    continue;
                }
                // 只信任真实存在于参数中的值，防止模型凭空编造路径作为授权依据
                if (!valueExistsInParams(parameters, item.getPath())) {
                    log.warn("Extracted path not present in params, ignored: {}", item.getPath());
                    continue;
                }
                accesses.add(PathAccess.builder()
                        .paramName(item.getParam())
                        .rawPath(item.getPath())
                        .direction(parseDirection(item.getDirection()))
                        .kind(PathKind.FILE)
                        .source(PathAccess.SOURCE_EXTRACTED)
                        .build());
            }
        }

        if (accesses.isEmpty()) {
            // 抽取不到路径 ≠ 放行，而是降级确认（D13）
            return ExtractionResult.uncertain("未抽取到路径，无法确认是否越界");
        }

        ExtractionResult result = new ExtractionResult();
        result.setPaths(accesses);
        // 模型自述不确定 → 仍需确认
        result.setUncertain(Boolean.TRUE.equals(json.getUncertain()));
        return result;
    }

    /**
     * 校验抽取出的路径确实出现在参数值中（含嵌套/数组），防止模型编造路径绕过校验。
     */
    private boolean valueExistsInParams(Map<String, Object> parameters, String path) {
        if (parameters == null || path == null) {
            return false;
        }
        String target = path.trim();
        for (Object value : parameters.values()) {
            if (containsValue(value, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsValue(Object value, String target) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return s.contains(target);
        }
        if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                if (containsValue(v, target)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object v : iterable) {
                if (containsValue(v, target)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private PathDirection parseDirection(String direction) {
        if (StringUtils.isBlank(direction)) {
            return PathDirection.READ;
        }
        try {
            return PathDirection.valueOf(direction.trim().toUpperCase());
        } catch (Exception e) {
            return PathDirection.READ;
        }
    }

    /**
     * 抽取结果。
     */
    @Data
    public static class ExtractionResult {

        /** 抽取到的路径访问事实 */
        private List<PathAccess> paths = new ArrayList<>();

        /** 是否不确定（需降级为确认） */
        private boolean uncertain;

        /** 不确定原因 */
        private String uncertainReason;

        public static ExtractionResult uncertain(String reason) {
            ExtractionResult result = new ExtractionResult();
            result.setUncertain(true);
            result.setUncertainReason(reason);
            return result;
        }
    }

    /**
     * 抽取输出 JSON 结构。
     */
    @Data
    public static class ExtractionJson {
        private List<PathItem> paths;
        private Boolean uncertain;
    }

    @Data
    public static class PathItem {
        private String param;
        private String path;
        private String direction;
    }
}
