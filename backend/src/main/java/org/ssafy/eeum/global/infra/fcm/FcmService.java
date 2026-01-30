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

    public void sendMessageTo(String token, String title, String body, String type, Long notificationId, String route) {
        if (token == null || token.isEmpty()) {
            return;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .putData("title", title)
                    .putData("body", body)
                    .putData("type", type != null ? type : "DEFAULT");

            if (notificationId != null) {
                messageBuilder.putData("notificationId", String.valueOf(notificationId));
            }

            if (route != null) {
                messageBuilder.putData("route", route);
            }

            Message message = messageBuilder.build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: " + response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM message", e);
        }
    }

    public void sendMulticast(List<String> tokens, String title, String body, String type, Long notificationId, String route) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        // 유효한 토큰만 필터링
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

            if (route != null) {
                builder.putData("route", route);
            }

            MulticastMessage message = builder.build();

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
