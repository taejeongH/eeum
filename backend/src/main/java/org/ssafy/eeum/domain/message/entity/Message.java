package org.ssafy.eeum.domain.message.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family group;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "voice_url", columnDefinition = "TEXT")
    private String voiceUrl;

    @Builder.Default
    @Column(name = "is_synced", nullable = false)
    private Boolean isSynced = false;

    @Builder.Default
    @Column(name = "is_read")
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "voice_task_id")
    private org.ssafy.eeum.domain.voice.entity.VoiceTask voiceTask;

    public void markRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void updateVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
        this.isSynced = false;
    }

    public void updateVoiceTask(org.ssafy.eeum.domain.voice.entity.VoiceTask voiceTask) {
        this.voiceTask = voiceTask;
    }
}