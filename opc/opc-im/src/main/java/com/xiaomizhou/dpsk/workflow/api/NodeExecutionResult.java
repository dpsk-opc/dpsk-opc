package com.xiaomizhou.dpsk.workflow.api;

import lombok.Builder;
import lombok.Getter;

/**
 * 节点执行结果，引擎无关。
 */
@Getter
@Builder
public class NodeExecutionResult {

    /** 是否执行成功，默认 true */
    @Builder.Default
    private boolean success = true;

    /** 路由节点（如 switch）的下一跳节点 id；普通节点为 null */
    private String routeId;

    /** 失败原因，success=false 时必填（用于 replan 反馈） */
    private String error;

    /** 成功结果 */
    public static NodeExecutionResult ok() {
        return NodeExecutionResult.builder().build();
    }

    /** 带路由的成功结果 */
    public static NodeExecutionResult route(String routeId) {
        return NodeExecutionResult.builder().routeId(routeId).build();
    }

    /** 失败结果，携带失败原因 */
    public static NodeExecutionResult fail(String error) {
        return NodeExecutionResult.builder().success(false).error(error).build();
    }
}
