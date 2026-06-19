package com.xiaomizhou.dpsk.core.ws.payload;

public record ToolCallPayload(String id,
                              String streamId,
                              String toolName,
                              Object arguments,
                              String result,
                              Object context) {
}
