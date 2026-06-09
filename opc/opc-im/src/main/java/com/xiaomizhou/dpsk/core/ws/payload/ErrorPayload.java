package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:31
 * @description
 */
public record ErrorPayload(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("details") Object details  // 可选
) {}
