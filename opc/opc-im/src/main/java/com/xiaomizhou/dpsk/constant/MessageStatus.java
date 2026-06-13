package com.xiaomizhou.dpsk.constant;

public interface MessageStatus {

    /**
     *
     * '消息状态: SENDING, SENT, DELIVERED, IGNORE-不会计入上下文,FAILED'
     */

    String SENDING = "SENDING";

    /**
     * 已发送
     */
    String SENT = "SENT";

    /**
     * 送达
     */
    String DELIVERED = "DELIVERED";

    /**
     * 忽略
     */
    String IGNORED = "IGNORE";
}
