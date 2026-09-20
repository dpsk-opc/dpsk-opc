package com.xiaomizhou.dpsk.tool.model;

import com.xiaomizhou.dpsk.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 工具执行结果。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {

    /** 执行状态：SUCCESS, PENDING, FAIL */
    private String status;

    /** 执行结果文本 */
    private String result;

    /** 错误信息 */
    private String errorMessage;

    /** 确认请求ID（当状态为 PENDING 时） */
    private String pendingRequestId;

    /** 执行耗时（毫秒） */
    private long executionTimeMs;

    /**
     * 错误码，用于上层（LangChain4jToolBridge / 幻觉兜底）结构化判断失败原因，
     * 决定是引导 LLM 先 add_tools、修正参数还是直接报错。
     */
    private String errorCode;

    /**
     * 结构化结果数据（执行器返回的原始对象/文本），内部流转使用，
     * 只在最后一步由 {@link #toLlmText()} 转成 String 返回给框架。
     */
    private Object payload;

    // ---- 常量 ----
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAIL = "FAIL";

    // ---- 错误码 ----

    /** 工具不存在（可能不存在，也可能未下发给 LLM） */
    public static final String ERROR_TOOL_NOT_FOUND = "TOOL_NOT_FOUND";

    /** 工具被禁用 */
    public static final String ERROR_TOOL_DISABLED = "TOOL_DISABLED";

    /** 工具不属于当前 Agent 可用范围 */
    public static final String ERROR_TOOL_NOT_IN_AGENT_SCOPE = "TOOL_NOT_IN_AGENT_SCOPE";

    /** 参数不合法 */
    public static final String ERROR_PARAM_INVALID = "PARAM_INVALID";

    /** Bean 或方法不存在（sourceRef 无法解析） */
    public static final String ERROR_BEAN_OR_METHOD_NOT_FOUND = "BEAN_OR_METHOD_NOT_FOUND";

    /** 执行过程中出错 */
    public static final String ERROR_EXECUTION_ERROR = "EXECUTION_ERROR";

    /** 执行超时 */
    public static final String ERROR_TIMEOUT = "TIMEOUT";

    /** 无权限 / 需要确认 */
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";

    /** 需要用户确认后执行 */
    public static final String ERROR_PENDING_CONFIRM = "PENDING_CONFIRM";

    /** 路径在工作空间之外且未获授权 */
    public static final String ERROR_PATH_OUT_OF_WORKSPACE = "PATH_OUT_OF_WORKSPACE";

    /** 路径命中系统受保护区域 */
    public static final String ERROR_PATH_PROTECTED = "PATH_PROTECTED";

    /** 未知错误 */
    public static final String ERROR_UNKNOWN = "UNKNOWN";

    /**
     * 创建成功结果。
     */
    public static ToolExecutionResult success(String result, long executionTimeMs) {
        return ToolExecutionResult.builder()
                .status(STATUS_SUCCESS)
                .result(result)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * 创建等待确认结果。
     */
    public static ToolExecutionResult pending(String requestId) {
        return ToolExecutionResult.builder()
                .status(STATUS_PENDING)
                .pendingRequestId(requestId)
                .build();
    }

    /**
     * 创建失败结果。
     */
    public static ToolExecutionResult fail(String errorMessage) {
        return fail(ERROR_UNKNOWN, errorMessage);
    }

    /**
     * 创建失败结果（带错误码）。
     */
    public static ToolExecutionResult fail(String errorCode, String errorMessage) {
        return ToolExecutionResult.builder()
                .status(STATUS_FAIL)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 是否成功。
     */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /**
     * 是否失败。
     */
    public boolean isFail() {
        return STATUS_FAIL.equals(status);
    }

    /**
     * 是否等待用户确认。
     */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    /**
     * 是否为「工具不存在/不可用」类错误 —— 上层据此引导 LLM 先调用 add_tools。
     */
    public boolean isToolMissing() {
        return ERROR_TOOL_NOT_FOUND.equals(errorCode)
                || ERROR_TOOL_NOT_IN_AGENT_SCOPE.equals(errorCode)
                || ERROR_TOOL_DISABLED.equals(errorCode);
    }

    /**
     * 从执行器返回值转换（{@code ToolExecutor} 的返回值即 payload）。
     */
    public static ToolExecutionResult ofPayload(Object payload, long executionTimeMs) {
        return ToolExecutionResult.builder()
                .status(STATUS_SUCCESS)
                .payload(payload)
                .result(payload == null ? null : payload.toString())
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * 转换为给 LLM 看的最终文本。
     * <p>
     * 框架（LangChain4j）只接受 String，因此这里是结构化结果流向框架的唯一出口，
     * 内部全流程保持结构化对象传递。
     */
    public String toLlmText() {
        if (STATUS_PENDING.equals(status)) {
            return "[工具需要用户确认，requestId=" + pendingRequestId + "]";
        }
        if (STATUS_FAIL.equals(status)) {
            return StringUtils.isNotBlank(errorMessage) ? errorMessage : "工具执行失败";
        }
        if (result != null) {
            return result;
        }
        if (payload == null) {
            return "工具执行完成，无返回内容";
        }
        return payload instanceof String s ? s : JsonUtils.toJson(payload);
    }

    @Override
    public String toString() {
        return "ToolExecutionResult{" +
                "status='" + status + '\'' +
                ", result='" + result + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", pendingRequestId='" + pendingRequestId + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}
