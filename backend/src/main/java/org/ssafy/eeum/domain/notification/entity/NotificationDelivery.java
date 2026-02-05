package org.ssafy.eeum.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_deliveries")
public class NotificationDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    private String channel;

    private LocalDateTime sentAt;

    private boolean isRead;

    private LocalDateTime readAt;

    @Builder
    public NotificationDelivery(User user, Notification notification, String channel) {
        this.user = user;
        this.notification = notification;
        this.channel = channel;
        this.isRead = false;
    }

    public void updateSentAt() {
        this.sentAt = LocalDateTime.now();
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
}
