package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 工作空间外路径访问确认事件（path_access_confirm）的载荷。
 * <p>
 * 模型自主访问工作空间之外的路径时，后端推送本事件，由用户决定是否放行。
 *
 * @param requestId      本次确认的唯一请求 ID（提交结果时原样带回）
 * @param conversationId 会话编码（前端据此路由到当前会话展示）
 * @param agentCode      发起访问的 Agent 编码
 * @param agentName      发起访问的 Agent 名称
 * @param toolName       触发访问的工具名
 * @param paths          待确认的路径列表
 * @param reason         模型自述的原因（可为空）
 * @param timeout        等待用户答复的超时时间（秒）
 */
public record PathAccessConfirmPayload(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("agentCode") String agentCode,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("toolName") String toolName,
        @JsonProperty("paths") List<PathItem> paths,
        @JsonProperty("reason") String reason,
        @JsonProperty("timeout") long timeout
) {

    /**
     * 单个待确认的路径。
     *
     * @param path      路径（工作空间外，绝对路径）
     * @param direction 访问方向：READ / WRITE / DELETE / LIST
     */
    public record PathItem(
            @JsonProperty("path") String path,
            @JsonProperty("direction") String direction
    ) {
    }
}
