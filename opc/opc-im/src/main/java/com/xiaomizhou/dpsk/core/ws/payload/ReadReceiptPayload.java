package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:30
 * @description
 */
public record ReadReceiptPayload(
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("lastReadMessageId") String lastReadMessageId
) {
}
