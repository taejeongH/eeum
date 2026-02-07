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
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class StreamingHandler extends BinaryWebSocketHandler {

    private final AtomicInteger logCounter = new AtomicInteger(0);

    // Device Session: deviceId -> Session
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    // Viewer Sessions: deviceId -> Set<Session> (1:N Broadcasting)
    private final Map<String, Set<WebSocketSession>> viewerSessions = new ConcurrentHashMap<>();

    private final org.ssafy.eeum.domain.iot.repository.IotDeviceRepository iotDeviceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StreamingHandler(org.ssafy.eeum.domain.iot.repository.IotDeviceRepository iotDeviceRepository) {
        this.iotDeviceRepository = iotDeviceRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {}", session.getId());

        // [Logic Update] Handle Query Parameters (e.g., ?familyId=8)
        try {
            java.net.URI uri = session.getUri();
            if (uri != null && uri.getQuery() != null) {
                String query = uri.getQuery();
                Map<String, String> queryParams = parseQueryParams(query);

                if (queryParams.containsKey("familyId")) {
                    int familyId = Integer.parseInt(queryParams.get("familyId"));
                    registerViewerByFamilyId(session, familyId);
                } else if (queryParams.containsKey("deviceId")) {
                    String deviceId = queryParams.get("deviceId");
                    registerViewerByDeviceId(session, deviceId);
                }
            }

            // [TEST MODE] Fallback removed as per request
            if (!session.getAttributes().containsKey("deviceId")) {
                log.info(
                        "ℹ️ No identity found in QueryParams. Connection maintained without subscribing to any device.");
            }

        } catch (Exception e) {
            log.warn("Failed to parse WebSocket query params: {}", e.getMessage());
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> queryPairs = new java.util.HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                queryPairs.put(
                        java.net.URLDecoder.decode(pair.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return queryPairs;
    }

    private void registerViewerByFamilyId(WebSocketSession session, int familyId) {
        String targetDeviceId = null;
        try {
            java.util.List<org.ssafy.eeum.domain.iot.entity.IotDevice> devices = iotDeviceRepository
                    .findAllByFamilyId(familyId);
            for (org.ssafy.eeum.domain.iot.entity.IotDevice device : devices) {
                if ("JETSON".equalsIgnoreCase(device.getDeviceType()) && device.getSerialNumber() != null) {
                    targetDeviceId = device.getSerialNumber();
                    log.info("Found Jetson device for Family {} via QueryParam: {}", familyId, targetDeviceId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error looking up device by familyId: {}", familyId, e);
        }

        if (targetDeviceId != null) {
            registerViewerByDeviceId(session, targetDeviceId);
        } else {
            log.warn("No Jetson device found for Family {} (QueryParam)", familyId);
        }
    }

    private void registerViewerByDeviceId(WebSocketSession session, String deviceId) {
        // Ensure the set is thread-safe
        viewerSessions.computeIfAbsent(deviceId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        viewerSessions.get(deviceId).add(session);

        session.getAttributes().put("deviceId", deviceId);
        session.getAttributes().put("role", "VIEWER");
        log.info("Viewer registered for device: {} (Session: {})", deviceId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Handle control messages (Register Device / Register Viewer)
        String payload = message.getPayload();
        try {
            JsonNode json = objectMapper.readTree(payload);

            // Check if type exists
            if (!json.has("type")) {
                // If it's just connection check or ping, ignore
                return;
            }

            String type = json.get("type").asText();

            if ("REGISTER_DEVICE".equals(type)) {
                if (!json.has("deviceId"))
                    return;
                String deviceId = json.get("deviceId").asText();

                deviceSessions.put(deviceId, session);
                session.getAttributes().put("deviceId", deviceId);
                session.getAttributes().put("role", "DEVICE");
                log.info("Device registered: {} (Session: {})", deviceId, session.getId());

            } else if ("REGISTER_VIEWER".equals(type)) {
                // [FIX] Ignore redundant registration if already registered (e.g. by QueryParam
                // or Fallback)
                if (session.getAttributes().containsKey("deviceId")) {
                    String currentDevice = (String) session.getAttributes().get("deviceId");
                    log.warn("Ignored redundant REGISTER_VIEWER request. Already watching: {}", currentDevice);
                    return;
                }

                // Logic moved to helper methods, but we support dynamic switching if needed
                if (json.has("familyId")) {
                    int fid = json.get("familyId").asInt();
                    registerViewerByFamilyId(session, fid);
                } else if (json.has("deviceId")) {
                    String did = json.get("deviceId").asText();
                    registerViewerByDeviceId(session, did);
                }
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
                ByteBuffer payload = message.getPayload();
                viewers.removeIf(viewer -> !viewer.isOpen());

                long currentTime = System.currentTimeMillis();

                for (WebSocketSession viewer : viewers) {
                    if (viewer.isOpen()) {
                        try {
                            // [Throttle] Limit to ~20 FPS (50ms interval) to prevent client/network
                            // overload
                            Long lastSent = (Long) viewer.getAttributes().getOrDefault("lastSentTime", 0L);
                            if (currentTime - lastSent < 50) {
                                continue;
                            }
                            viewer.getAttributes().put("lastSentTime", currentTime);

                            // [Log Sample] Log only every 30th frame to reduce server I/O
                            if (logCounter.incrementAndGet() % 30 == 0) {
                                log.info("Sending binary frame to viewer: {} (Size: {} bytes)", viewer.getId(),
                                        payload.remaining());
                            }

                            viewer.sendMessage(new BinaryMessage(payload.duplicate()));
                        } catch (IOException e) {
                            log.debug("Failed to send frame to viewer: {}", viewer.getId());
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
            log.info("Device disconnected: {} (Code: {}, Reason: {})", deviceId, status.getCode(), status.getReason());
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
