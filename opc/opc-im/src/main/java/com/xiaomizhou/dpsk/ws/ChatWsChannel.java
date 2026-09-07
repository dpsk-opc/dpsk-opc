package com.xiaomizhou.dpsk.ws;

import com.xiaomizhou.dpsk.core.ws.WsMsgType;
import com.xiaomizhou.dpsk.core.ws.WsMessage;
import com.xiaomizhou.dpsk.core.ws.payload.PingPongPayload;
import com.xiaomizhou.dpsk.utils.JsonUtils;
import com.xiaomizhou.dpsk.core.utils.WsUtils;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Objects;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 14:36
 * @description
 */
@ServerEndpoint(value = "/xiaomizhou/opc/ws")
@Slf4j
public class ChatWsChannel {


    private Session session;

    /**
     * 统一发送锁，保证同一连接上的所有发送（PING/PONG 与业务推送）串行，避免并发写入冲突
     */
    private final Object writeLock = new Object();

    public ChatWsChannel() {
        WsUtils.channel = this;
    }

    @OnMessage
    public void onMessage(String message) throws IOException {

        log.debug("received message: {}", message);

        WsMessage wm = JsonUtils.toObj(message, WsMessage.class);

        String type = wm.type();

        switch (type) {
            case WsMsgType.PING:
                send(JsonUtils.toJson(new WsMessage(WsMsgType.PONG, new PingPongPayload(System.nanoTime()))));
                break;
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        log.info("new connection: {}", session.getId());
        this.session = session;
    }

    @OnClose
    public void onClose(Session session) {
        log.info("connection closed: {}", session.getId());
        session = null;
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        log.error("connection error: {}", session.getId(), throwable);
    }

    public class WsCloseException extends Exception {

        public WsCloseException() {
            super();
        }

        public WsCloseException(String message) {
            super(message);
        }

        public WsCloseException(String message, Throwable cause) {
            super(message, cause);
        }

        public WsCloseException(Throwable cause) {
            super(cause);
        }

        public WsCloseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    /**
     * 发送消息
     *
     * @param msg
     * @return
     * @throws WsCloseException
     */
    public boolean to(WsMessage msg) throws WsCloseException, IOException {

        if (Objects.isNull(msg)) {
            log.warn("message can not be null.");
            return false;
        }

        if (session == null) {
            throw new WsCloseException("ws closed.");
        }
        send(JsonUtils.toJson(msg));
        return true;
    }

    private void send(String json) throws IOException {
        synchronized (writeLock) {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(json);
            }
        }
    }

}
