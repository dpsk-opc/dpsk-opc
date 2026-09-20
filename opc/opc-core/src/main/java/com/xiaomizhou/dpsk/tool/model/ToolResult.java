package com.xiaomizhou.dpsk.tool.model;

import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 工具执行器层的结构化返回结果。
 * <p>
 * 执行器不再直接返回 String（那样异常会被拼成字符串、上层无法判断成败），
 * 而是返回本对象，由 {@link ToolInvocationInterceptor} 汇总为
 * {@link ToolExecutionResult}，最终在 {@link ToolExecutionResult#toLlmText()} 处
 * 才转成 String 交给 LangChain4j 框架。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/9/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 是否执行成功 */
    @Builder.Default
    private boolean success = true;

    /** 结构化结果数据（内部流转用，真正的数据载体） */
    private Object data;

    /** 结果文本（成功时的摘要，失败时的错误描述） */
    private String text;

    /** 错误码，见 {@link ToolExecutionResult} 的 ERROR_* 常量 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private long executionTimeMs;

    public static ToolResult success(Object data) {
        return ToolResult.builder()
                .success(true)
                .data(data)
                .build();
    }

    public static ToolResult successText(String text) {
        return ToolResult.builder()
                .success(true)
                .data(text)
                .text(text)
                .build();
    }

    public static ToolResult fail(String errorCode, String errorMessage) {
        return ToolResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建失败结果（自动从异常链提取有用信息）。
     * <p>
     * 取的是<b>最内层</b>的业务异常，而不是外层包装（如 ExecutionException）。
     * 若异常 message 为空（如 NPE、requireNonNull 失败），则补充异常类型与
     * 首条业务栈帧（类名.方法名:行号），避免只回传 "xxxException: null"
     * 导致模型无法定位问题而反复重试。
     */
    public static ToolResult fail(String errorCode, String errorMessage, Throwable cause) {
        return fail(errorCode, errorMessage, cause, 600);
    }

    /**
     * 创建失败结果（可控制栈帧回溯深度）。
     */
    public static ToolResult fail(String errorCode, String errorMessage, Throwable cause, int depth) {
        String detail = describeCause(cause, depth);
        if (StringUtils.isBlank(detail)) {
            return fail(errorCode, errorMessage);
        }
        return fail(errorCode, StringUtils.isBlank(errorMessage) ? detail : errorMessage + "；原因: " + detail);
    }

    /**
     * 描述异常根因（供错误回调等场景复用）：类型 + message，message 为空时补充业务栈帧。
     */
    public static String describeError(Throwable cause) {
        return describeCause(cause, 600);
    }

    /**
     * 描述异常根因：类型 + message + 首个业务栈帧。
     * <p>
     * 遍历到最内层 cause（ExecutionException / InvocationTargetException 等包装层会被剥掉），
     * message 为空时用栈帧兜底。
     */
    private static String describeCause(Throwable cause, int depth) {
        if (cause == null) {
            return null;
        }
        // 剥掉包装层（ExecutionException / InvocationTargetException 等）
        Throwable root = cause;
        for (int i = 0; i < depth && root.getCause() != null && root.getCause() != root; i++) {
            root = root.getCause();
        }

        StringBuilder sb = new StringBuilder(root.getClass().getSimpleName());
        if (StringUtils.isNotBlank(root.getMessage())) {
            sb.append(": ").append(StringUtils.abbreviate(root.getMessage(), 300));
        }

        // message 为空时，补充首个业务栈帧，便于定位
        if (StringUtils.isBlank(root.getMessage())) {
            String frame = firstBusinessFrame(root);
            if (frame != null) {
                sb.append("（位置: ").append(frame).append("）");
            } else {
                sb.append("（无错误信息）");
            }
        }
        return sb.toString();
    }

    /**
     * 取首个业务栈帧（跳过 JDK / 框架内部帧）。
     */
    private static String firstBusinessFrame(Throwable root) {
        for (StackTraceElement frame : root.getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("java.")
                    || className.startsWith("jdk.")
                    || className.startsWith("sun.")
                    || className.startsWith("org.springframework.")
                    || className.startsWith("org.apache.ibatis.")
                    || className.startsWith("com.zaxxer.")) {
                continue;
            }
            return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return null;
    }

    public ToolResult withExecutionTime(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
        return this;
    }

    /**
     * 获取用于日志/回传的文本表示。
     */
    public String getResultText() {
        if (StringUtils.isNotBlank(text)) {
            return text;
        }
        if (success) {
            return data == null ? null : (data instanceof String s ? s : JsonUtils.toJson(data));
        }
        return errorMessage;
    }
}
