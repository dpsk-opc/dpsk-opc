package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    // ---- 常量 ----
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAIL = "FAIL";

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
        return ToolExecutionResult.builder()
                .status(STATUS_FAIL)
                .errorMessage(errorMessage)
                .build();
    }

    @Override
    public String toString() {
        return "ToolExecutionResult{" +
                "status='" + status + '\'' +
                ", result='" + result + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", pendingRequestId='" + pendingRequestId + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}
