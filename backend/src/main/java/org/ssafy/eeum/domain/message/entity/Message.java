package org.ssafy.eeum.domain.message.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.family.entity.Family;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "voice_url", columnDefinition = "TEXT")
    private String voiceUrl;

    @Column(name = "is_synced", nullable = false)
    private Boolean isSynced;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_read", columnDefinition = "TINYINT")
    private Boolean isRead;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public Message(Family group, User sender, String content, String voiceUrl) {
        this.group = group;
        this.sender = sender;
        this.content = content;
        this.voiceUrl = voiceUrl;
        this.isRead = false;
        this.isSynced = false;
    }

    public void updateVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
        this.isSynced = false;
    }

    public void markSynced() {
        this.isSynced = true;
    }

    public void markRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
