package com.ubs.orkestra.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.ubs.orkestra.websocket.FlowLogWebSocketHandler;
import org.slf4j.MDC;

import java.util.UUID;

public class FlowExecutionLogAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        String flowExecutionIdStr = MDC.get("flowExecutionId");
        if (flowExecutionIdStr != null) {
            try {
                UUID flowExecutionId = UUID.fromString(flowExecutionIdStr);
                FlowLogWebSocketHandler.sendMessage(flowExecutionId, event.getFormattedMessage());
            } catch (IllegalArgumentException e) {
                // Not a valid UUID, so we don't forward this log
            }
        }
    }
}