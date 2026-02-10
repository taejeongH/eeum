package org.ssafy.eeum.global.infra.fcm;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Firebase Cloud Messaging(FCM)를 통해 푸시 알림 전송을 담당하는 서비스 클래스입니다.
 * 
 * @summary FCM 알림 전송 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private static final String DEFAULT_TYPE = "DEFAULT";
    private static final String UNREGISTERED_ERROR = "UNREGISTERED";

    /**
     * 특정 디바이스 토큰으로 단일 푸시 알림을 전송합니다.
     * 
     * @summary 단일 FCM 메시지 전송
     * @param token          대상 디바이스 토큰
     * @param title          알림 제목
     * @param body           알림 내용
     * @param type           알림 타입 (DEFAULT, FALL 등)
     * @param notificationId DB에 저장된 알림 식별자
     * @param route          앱 내 이동 경로
     * @param familyId       가족 식별자
     * @param groupName      가족 그룹명
     * @param eventId        관련 이벤트 식별자 (낙상 등)
     * @throws FcmUnregisteredTokenException 토큰이 만료되었을 때 발생
     */
    public void sendMessageTo(String token, String title, String body, String type, Long notificationId, String route,
            Integer familyId, String groupName, Integer eventId) {
        if (token == null || token.isEmpty()) {
            return;
        }

        try {
            Message message = createMessage(token, title, body, type, notificationId, route, familyId, groupName,
                    eventId);
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 메시지 전송 성공: {}", response);
        } catch (FirebaseMessagingException e) {
            handleFirebaseMessagingException(e, token);
        }
    }

    /**
     * 여러 개의 토큰을 가진 디바이스들로 푸시 알림을 전송합니다.
     * 
     * @summary 다중 FCM 메시지 전송
     * @param tokens         대상 디바이스 토큰 리스트
     * @param title          알림 제목
     * @param body           알림 내용
     * @param type           알림 타입
     * @param notificationId 알림 식별자
     * @param route          이동할 앱 경로
     * @param familyId       가족 식별자
     */
    public void sendMulticast(List<String> tokens, String title, String body, String type, Long notificationId,
            String route, Integer familyId) {
        List<String> validTokens = filterValidTokens(tokens);
        if (validTokens.isEmpty()) {
            return;
        }

        try {
            List<Message> messages = validTokens.stream()
                    .map(token -> createMessage(token, title, body, type, notificationId, route, familyId, null, null))
                    .collect(Collectors.toList());

            BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages);
            log.info("FCM 다중 메시지 전송 성공. 성공 개수: {}", response.getSuccessCount());
            logFailures(response);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 다중 메시지 전송 실패", e);
        }
    }

    private Message createMessage(String token, String title, String body, String type, Long notificationId,
            String route,
            Integer familyId, String groupName, Integer eventId) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setAndroidConfig(createAndroidConfig());

        addData(builder::putData, title, body, type, notificationId, route, familyId, groupName, eventId);
        return builder.build();
    }

    private void addData(BiConsumer<String, String> dataAdder, String title, String body,
            String type,
            Long notificationId, String route, Integer familyId, String groupName, Integer eventId) {

        dataAdder.accept("type", type != null ? type : DEFAULT_TYPE);
        if (title != null)
            dataAdder.accept("title", title);
        if (body != null)
            dataAdder.accept("body", body);
        if (notificationId != null)
            dataAdder.accept("notificationId", String.valueOf(notificationId));
        if (familyId != null)
            dataAdder.accept("familyId", String.valueOf(familyId));
        if (groupName != null)
            dataAdder.accept("groupName", groupName);
        if (route != null)
            dataAdder.accept("route", route);
        if (eventId != null)
            dataAdder.accept("eventId", String.valueOf(eventId));
    }

    private AndroidConfig createAndroidConfig() {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build();
    }

    private List<String> filterValidTokens(List<String> tokens) {
        if (tokens == null)
            return List.of();
        return tokens.stream()
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.toList());
    }

    private void handleFirebaseMessagingException(FirebaseMessagingException e, String token) {
        if (UNREGISTERED_ERROR.equals(e.getMessagingErrorCode().name())) {
            log.warn("FCM 토큰이 등록 해제되었습니다. 정리를 위해 표시함: {}", token);
            throw new FcmUnregisteredTokenException(token, "FCM 토큰이 등록 해제되었습니다.", e);
        }
        log.error("FCM 메시지 전송 실패", e);
    }

    private void logFailures(BatchResponse response) {
        if (response.getFailureCount() > 0) {
            List<String> errorMessages = response.getResponses().stream()
                    .filter(r -> !r.isSuccessful())
                    .map(r -> r.getException().getMessage())
                    .collect(Collectors.toList());
            log.warn("FCM 멀티캐스트 전송 실패 ({}건): {}", response.getFailureCount(), errorMessages);
        }
    }
}
