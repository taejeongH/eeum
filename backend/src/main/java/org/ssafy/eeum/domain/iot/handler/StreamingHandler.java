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
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IoT 기기(Jetson)로부터 전송되는 영상 스트리밍 데이터를 뷰어에게 중계하는 WebSocket 핸들러입니다.
 * 1:N 방식의 브로드캐스팅을 지원하며, 기기와 뷰어의 세션을 관리합니다.
 * 
 * @summary 실시간 영상 스트리밍 중계 핸들러
 */
@Slf4j
@Component
public class StreamingHandler extends BinaryWebSocketHandler {

    private final AtomicInteger logCounter = new AtomicInteger(0);

    // Device Session: deviceId -> Session
    private final Map<String, WebSocketSession> deviceSessions = new ConcurrentHashMap<>();

    // Viewer Sessions: deviceId -> Set<Session> (1:N Broadcasting)
    private final Map<String, Set<WebSocketSession>> viewerSessions = new ConcurrentHashMap<>();

    private final IotDeviceRepository iotDeviceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StreamingHandler(IotDeviceRepository iotDeviceRepository) {
        this.iotDeviceRepository = iotDeviceRepository;
    }

    /**
     * WebSocket 연결이 확립된 후 호출됩니다.
     * 쿼리 파라미터(familyId 또는 deviceId)를 통해 뷰어의 구독 대상을 식별하고 등록합니다.
     * 
     * @summary WebSocket 연결 확립 처리
     * @param session 연결된 WebSocket 세션
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {} | Client IP: {}", session.getId(), session.getRemoteAddress());

        try {
            URI uri = session.getUri();
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

            if (!session.getAttributes().containsKey("deviceId")) {
                log.info(
                        "ℹ️ No identity found in QueryParams. Connection maintained without subscribing to any device.");
            }

        } catch (Exception e) {
            log.warn("Failed to parse WebSocket query params: {}", e.getMessage());
        }
    }

    /**
     * URL 쿼리 문자열을 파싱하여 Map 형태로 반환합니다.
     * 
     * @summary 쿼리 파라미터 파싱
     * @param query 쿼리 문자열
     * @return 파싱된 파라미터 Map
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> queryPairs = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                queryPairs.put(
                        URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return queryPairs;
    }

    /**
     * 가족 ID를 기반으로 해당 가족의 Jetson 기기를 찾아 뷰어를 등록합니다.
     * 
     * @summary 가족 ID 기반 뷰어 등록
     * @param session  WebSocket 세션
     * @param familyId 가족 그룹 식별자
     */
    private void registerViewerByFamilyId(WebSocketSession session, int familyId) {
        String targetDeviceId = null;
        try {
            List<IotDevice> devices = iotDeviceRepository
                    .findAllByFamilyId(familyId);
            for (IotDevice device : devices) {
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

    /**
     * 기기 ID를 기반으로 뷰어를 해당 기기의 방송 세션에 등록합니다.
     * 
     * @summary 기기 ID 기반 뷰어 등록
     * @param session  WebSocket 세션
     * @param deviceId 기기 시리얼 번호
     */
    private void registerViewerByDeviceId(WebSocketSession session, String deviceId) {
        viewerSessions.computeIfAbsent(deviceId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        viewerSessions.get(deviceId).add(session);

        session.getAttributes().put("deviceId", deviceId);
        session.getAttributes().put("role", "VIEWER");
        log.info("Viewer registered for device: {} (Session: {})", deviceId, session.getId());
    }

    /**
     * 클라이언트로부터 받은 텍스트 메시지를 처리합니다.
     * 기기 등록(REGISTER_DEVICE) 또는 뷰어 등록(REGISTER_VIEWER) 제어 메시지를 처리합니다.
     * 
     * @summary 텍스트 메시지(제어) 처리
     * @param session WebSocket 세션
     * @param message 텍스트 메시지
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.info("Received Text Message from {}: {}", session.getId(), payload);

        try {
            JsonNode json = objectMapper.readTree(payload);

            if (!json.has("type")) {
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
                if (session.getAttributes().containsKey("deviceId")) {
                    String currentDevice = (String) session.getAttributes().get("deviceId");
                    log.warn("Ignored redundant REGISTER_VIEWER request. Already watching: {}", currentDevice);
                    return;
                }

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

    /**
     * 기기로부터 전송된 이진 데이터(영상 프레임)를 해당 기기를 구독 중인 모든 관찰자(Viewer)에게 중계합니다.
     * 과도한 네트워크 부하를 방지하기 위해 50ms 간격으로 전송을 제한(Throttle)합니다.
     * 
     * @summary 이진 메시지(영상 프레임) 중계 처리
     * @param session 데이터를 보낸 세션 (기기)
     * @param message 이진 데이터 메시지
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        if (logCounter.get() % 30 == 0) {
            log.info("Received binary frame from: {} (Size: {} bytes)", session.getId(), message.getPayloadLength());
        }

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
                            Long lastSent = (Long) viewer.getAttributes().getOrDefault("lastSentTime", 0L);
                            if (currentTime - lastSent < 50) {
                                continue;
                            }
                            viewer.getAttributes().put("lastSentTime", currentTime);

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

    /**
     * 전송 중 발생한 오류를 처리합니다.
     * 
     * @summary 전송 오류 처리
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket Transport Error: Session={}, Error={}", session.getId(), exception.getMessage(),
                exception);
        super.handleTransportError(session, exception);
    }

    /**
     * WebSocket 연결이 종료된 후 호출됩니다.
     * 등록된 기기 또는 뷰어 세션을 관리 목록에서 제거합니다.
     * 
     * @summary WebSocket 연결 종료 처리
     */
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
