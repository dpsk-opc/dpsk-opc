package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaomizhou.dpsk.core.ws.SenderInfo;

import java.util.Map;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:27
 * @description
 */
public record MessagePayload(
        @JsonProperty("id") String messageId,
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("taskId") String taskId,
        @JsonProperty("type") String messageType,   // text, image, file, etc.
        @JsonProperty("content") Object content,     // 文本内容 或 文件URL
        @JsonProperty("sender") SenderInfo sender,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("replyToId") String replyToId,      // 可选
        @JsonProperty("metadata") Map<String, Object> metadata  // 可选
) {}
