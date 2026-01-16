package com.ubs.orkestra.config;

import com.ubs.orkestra.websocket.FlowLogWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(flowLogWebSocketHandler(), "/ws/flow-logs/{flowExecutionUUID}")
                .setAllowedOrigins("*");
    }

    @Bean
    public FlowLogWebSocketHandler flowLogWebSocketHandler() {
        return new FlowLogWebSocketHandler();
    }
}
