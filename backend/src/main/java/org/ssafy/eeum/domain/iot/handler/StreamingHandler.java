package org.ssafy.eeum.domain.iot.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StreamingHandler extends BinaryWebSocketHandler {

    // Device Session: deviceId -> Session
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    // Viewer Sessions: deviceId -> Set<Session> (1:N Broadcasting)
    private final Map<String, Set<WebSocketSession>> viewerSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Handle control messages (Register Device / Register Viewer)
        String payload = message.getPayload();
        try {
            JsonNode json = objectMapper.readTree(payload);

            // Check if type exists
            if (!json.has("type")) {
                log.warn("Message missing 'type': {}", payload);
                return;
            }

            String type = json.get("type").asText();

            // Check if deviceId exists
            if (!json.has("deviceId")) {
                log.warn("Message missing 'deviceId': {}", payload);
                return;
            }

            String deviceId = json.get("deviceId").asText();

            if ("REGISTER_DEVICE".equals(type)) {
                deviceSessions.put(deviceId, session);
                session.getAttributes().put("deviceId", deviceId);
                session.getAttributes().put("role", "DEVICE");
                log.info("Device registered: {} (Session: {})", deviceId, session.getId());

            } else if ("REGISTER_VIEWER".equals(type)) {
                // Ensure the set is thread-safe
                viewerSessions.computeIfAbsent(deviceId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                viewerSessions.get(deviceId).add(session);

                session.getAttributes().put("deviceId", deviceId);
                session.getAttributes().put("role", "VIEWER");
                log.info("Viewer registered for device: {} (Session: {})", deviceId, session.getId());

            } else {
                log.warn("Unknown message type: {}", type);
            }

        } catch (Exception e) {
            log.error("Error handling text message: {}", payload, e);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // Relay binary data (JPEG Frame) from Device to Viewers
        String role = (String) session.getAttributes().get("role");
        String deviceId = (String) session.getAttributes().get("deviceId");

        if ("DEVICE".equals(role) && deviceId != null) {
            Set<WebSocketSession> viewers = viewerSessions.get(deviceId);
            if (viewers != null && !viewers.isEmpty()) {

                // Get payload logic
                ByteBuffer payload = message.getPayload();

                // Iterate carefully
                viewers.removeIf(viewer -> !viewer.isOpen());

                for (WebSocketSession viewer : viewers) {
                    if (viewer.isOpen()) {
                        try {
                            // Send a new BinaryMessage wrapping a duplicate of the buffer
                            // This ensures independent position/limit for each send if the underlying
                            // container relies on it
                            viewer.sendMessage(new BinaryMessage(payload.duplicate()));
                        } catch (IOException e) {
                            log.error("Failed to send frame to viewer: {}", viewer.getId(), e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String role = (String) session.getAttributes().get("role");
        String deviceId = (String) session.getAttributes().get("deviceId");

        if ("DEVICE".equals(role) && deviceId != null) {
            deviceSessions.remove(deviceId);
            log.info("Device disconnected: {}", deviceId);
        } else if ("VIEWER".equals(role) && deviceId != null) {
            Set<WebSocketSession> viewers = viewerSessions.get(deviceId);
            if (viewers != null) {
                viewers.remove(session);
                if (viewers.isEmpty()) {
                    viewerSessions.remove(deviceId);
                }
            }
            log.info("Viewer disconnected: {} (Code: {}, Reason: {})", deviceId, status.getCode(), status.getReason());
        } else {
            log.info("Unknown connection closed: {} (Code: {}, Reason: {})", session.getId(), status.getCode(),
                    status.getReason());
        }
    }
}
