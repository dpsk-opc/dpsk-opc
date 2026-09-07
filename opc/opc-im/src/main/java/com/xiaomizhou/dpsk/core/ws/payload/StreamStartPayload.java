package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:26
 * @description
 */
public record StreamStartPayload(
        @JsonProperty("streamId") String streamId,
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("taskId") String taskId,
        @JsonProperty("sender") SenderInfo sender,
        @JsonProperty("timestamp") long timestamp) {
}
