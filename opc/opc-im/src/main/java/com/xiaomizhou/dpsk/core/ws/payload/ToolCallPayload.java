package com.xiaomizhou.dpsk.core.ws.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ToolCallPayload(String id,
                              String streamId,
                              @JsonProperty("taskId") String taskId,
                              String toolName,
                              Object arguments,
                              String result,
                              Object context) {
}
