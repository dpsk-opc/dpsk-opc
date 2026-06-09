package com.xiaomizhou.dpsk.core.config;

import com.xiaomizhou.dpsk.ws.ChatWsChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * @author eason - vipzhsh@163.com
 * @date 2026/5/22 16:43
 * @description
 */
@Configuration
public class WebsocketConfig {

    @Bean
    public ServerEndpointExporter newEndpointExporter() {

        ServerEndpointExporter exporter = new ServerEndpointExporter();

        // 手动注册 WebSocket 端点
        exporter.setAnnotatedEndpointClasses(ChatWsChannel.class);
        return exporter;
    }
}
