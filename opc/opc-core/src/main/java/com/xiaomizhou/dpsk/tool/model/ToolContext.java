package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行上下文，包含会话、Agent、用户等信息，用于参数动态补全。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolContext {

    /** Agent 编码 */
    private String agentCode;

    /** 用户编码 */
    private String userCode;

    /** 会话编码 */
    private String conversationCode;

    /** 链路追踪ID */
    private String traceId;

    /** 扩展属性（环境变量等） */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}
