package org.ssafy.eeum.global.infra.fcm;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    public void sendMessageTo(String token, String title, String body, String type, Long notificationId, String route,
            Integer familyId, String groupName, Integer eventId) {
        if (token == null || token.isEmpty()) {
            return;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .putData("type", type != null ? type : "DEFAULT");

            if (title != null) {
                messageBuilder.putData("title", title);
            }
            if (body != null) {
                messageBuilder.putData("body", body);
            }

            if (notificationId != null) {
                messageBuilder.putData("notificationId", String.valueOf(notificationId));
            }

            if (familyId != null) {
                messageBuilder.putData("familyId", String.valueOf(familyId));
            }

            if (groupName != null) {
                messageBuilder.putData("groupName", groupName);
            }

            if (route != null) {
                messageBuilder.putData("route", route);
            }

            if (eventId != null) {
                messageBuilder.putData("eventId", String.valueOf(eventId));
            }

            Message message = messageBuilder
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: " + response);
        } catch (FirebaseMessagingException e) {
            if ("UNREGISTERED".equals(e.getMessagingErrorCode().name())) {
                log.warn("FCM token is unregistered. Marking for cleanup: {}", token);
                throw new FcmUnregisteredTokenException(token, "FCM token is unregistered", e);
            }
            log.error("Failed to send FCM message", e);
        }
    }

    public void sendMulticast(List<String> tokens, String title, String body, String type, Long notificationId,
            String route, Integer familyId) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        
        List<String> validTokens = tokens.stream()
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.toList());

        if (validTokens.isEmpty()) {
            return;
        }

        try {
            com.google.firebase.messaging.MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(validTokens)
                    .putData("title", title)
                    .putData("body", body)
                    .putData("type", type != null ? type : "DEFAULT");

            if (notificationId != null) {
                builder.putData("notificationId", String.valueOf(notificationId));
            }

            if (familyId != null) {
                builder.putData("familyId", String.valueOf(familyId));
            }

            if (route != null) {
                builder.putData("route", route);
            }

            MulticastMessage message = builder
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            log.info("Successfully sent multicast message. Success count: " + response.getSuccessCount());

            if (response.getFailureCount() > 0) {
                log.warn("List of tokens that caused failures: " + response.getResponses().stream()
                        .filter(r -> !r.isSuccessful())
                        .map(SendResponse::getException)
                        .collect(Collectors.toList()));
            }
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send multicast FCM message", e);
        }
    }
}
