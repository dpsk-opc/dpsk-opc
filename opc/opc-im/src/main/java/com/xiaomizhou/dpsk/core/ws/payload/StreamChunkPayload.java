package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:26
 * @description
 */
public record StreamChunkPayload(
        @JsonProperty("streamId") String streamId,
        @JsonProperty("delta") String delta,
        @JsonProperty("sequence") int sequence
) {
}
