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

    /** 成功结果 */
    public static NodeExecutionResult ok() {
        return NodeExecutionResult.builder().build();
    }

    /** 带路由的成功结果 */
    public static NodeExecutionResult route(String routeId) {
        return NodeExecutionResult.builder().routeId(routeId).build();
    }
}
