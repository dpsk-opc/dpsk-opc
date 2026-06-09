package com.xiaomizhou.dpsk.core.ws;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:28
 * @description
 */

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 消息发送者信息（广播时携带）
 */
public record SenderInfo(
        @JsonProperty("userId") String userId,
        @JsonProperty("name") String name,
        @JsonProperty("avatar") String avatar
) {
}
