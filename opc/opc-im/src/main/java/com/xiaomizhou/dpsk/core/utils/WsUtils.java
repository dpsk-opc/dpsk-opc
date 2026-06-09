package com.xiaomizhou.dpsk.core.utils;

import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.ws.ChatWsChannel;

import java.io.IOException;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 17:23
 * @description
 */
public class WsUtils {

    public static ChatWsChannel channel;

    private WsUtils() {

    }

    /**
     * 发送消息
     *
     * @param msg
     * @return
     * @throws ChatWsChannel.WsCloseException
     */
    public static boolean send(WsMessage msg) throws ChatWsChannel.WsCloseException, IOException {
        return channel.to(msg);
    }



}
