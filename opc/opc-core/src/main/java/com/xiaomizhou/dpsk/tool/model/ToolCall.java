package com.xiaomizhou.dpsk.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用请求，封装 LLM 发起的工具调用。
 *
 * @author eason - vipzhsh@163.com
 * @date 2026/5/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 工具名称（对应 ToolMetadata.name） */
    private String name;

    /** 调用参数 */
    private Map<String, Object> parameters;

    /** 调用 ID（用于确认/追踪） */
    private String callId;

    /** 是否已被用户确认 */
    @Builder.Default
    private boolean confirmed = false;
}
