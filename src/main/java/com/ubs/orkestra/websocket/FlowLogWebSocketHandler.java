package com.ubs.orkestra.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FlowLogWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(FlowLogWebSocketHandler.class);
    private static final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID flowExecutionUUID = getFlowExecutionUUID(session);
        if (flowExecutionUUID != null) {
            sessions.put(flowExecutionUUID, session);
            logger.info("WebSocket connection established for flowExecutionUUID: {}", flowExecutionUUID);
        } else {
            logger.error("flowExecutionUUID is null, closing connection");
            session.close(CloseStatus.BAD_DATA.withReason("flowExecutionUUID not found in URI"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID flowExecutionUUID = getFlowExecutionUUID(session);
        if (flowExecutionUUID != null) {
            sessions.remove(flowExecutionUUID);
            logger.info("WebSocket connection closed for flowExecutionUUID: {}. Status: {}", flowExecutionUUID, status);
        }
    }

    public static void sendMessage(UUID flowExecutionUUID, String message) {
        WebSocketSession session = sessions.get(flowExecutionUUID);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                logger.error("Failed to send message to flowExecutionUUID: {}", flowExecutionUUID, e);
            }
        }
    }

    private UUID getFlowExecutionUUID(WebSocketSession session) {
        try {
            String path = session.getUri().getPath();
            String[] segments = path.split("/");
            return UUID.fromString(segments[segments.length - 1]);
        } catch (Exception e) {
            logger.error("Failed to extract flowExecutionUUID from URI: {}", session.getUri(), e);
            return null;
        }
    }
}
