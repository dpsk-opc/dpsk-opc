package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:25
 * @description
 */
public record StreamEndPayload(
        @JsonProperty("streamId") String streamId,
        @JsonProperty("fullMessageId") String fullMessageId,   // 消息入库后的ID
        @JsonProperty("fullContent") Object fullContent        // 完整内容（可选）
) {}
