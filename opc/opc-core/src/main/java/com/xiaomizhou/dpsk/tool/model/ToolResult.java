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

    public static ToolResult fail(String errorCode, String errorMessage, Throwable cause) {
        Throwable root = cause;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root == null ? errorMessage : root.getClass().getSimpleName() + ": " + root.getMessage();
        return fail(errorCode, StringUtils.isBlank(errorMessage) ? detail : errorMessage + " (" + detail + ")");
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
