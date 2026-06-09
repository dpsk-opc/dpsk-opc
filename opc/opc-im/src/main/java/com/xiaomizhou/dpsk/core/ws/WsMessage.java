package com.xiaomizhou.dpsk.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * WebSocket 消息的顶层容器。
 * 客户端发送/服务端广播都使用此结构。
 *
 * @param type    消息类型，见 {@link WsMsgType}
 * @param payload 具体数据，根据 type 不同，payload 结构不同
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:19
 * @description
 */
public record WsMessage(
        @JsonProperty("type") String type,
        @JsonProperty("payload") Object payload
) {
}
