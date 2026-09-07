package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent 向用户提问事件（tool_ask）的载荷。
 * <p>
 * 后端在 AskUserTool 执行时推送给前端，前端据此展示问题并让用户输入答案。
 * <p>
 * 注意：字段名与项目 WS 协议保持一致（与其他 payload 统一使用 conversationId）。
 *
 * @param requestId        本次提问的唯一请求 ID（提交答案时原样带回）
 * @param conversationId   会话编码（用于前端路由到当前会话展示）
 * @param question         向用户提出的问题
 * @param timeout          等待用户输入的超时时间（秒）
 */
public record ToolAskPayload(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("question") String question,
        @JsonProperty("timeout") long timeout
) {
}
