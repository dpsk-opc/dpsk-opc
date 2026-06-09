package com.xiaomizhou.dpsk.core.ws;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:20
 * @description
 */
public interface WsMsgType {

    // 普通消息（非流式）
    String MESSAGE = "message";

    // 流式消息开始
    String STREAM_START = "stream_start";
    // 流式消息块
    String STREAM_CHUNK = "stream_chunk";
    // 流式消息结束
    String STREAM_END = "stream_end";

    // 心跳 ping/pong
    String PING = "ping";

    String PONG = "pong";

    // 已读回执
    String READ_RECEIPT = "read_receipt";

    // 错误（服务端主动推送）
    String ERROR = "error";
    
}
