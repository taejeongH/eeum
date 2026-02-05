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

    // 배포 DB가 TEXT 타입이므로 @Lob 대신 columnDefinition 사용이 더 안전함
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

    @Column(name = "tts_job_id")
    private String ttsJobId;

    @Builder.Default
    @Column(name = "tts_poll_count")
    private Integer ttsPollCount = 0;

    @Column(name = "last_tts_polled_at")
    private LocalDateTime lastTtsPolledAt;

    /**
     * 로직 메서드
     */
    public void markRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    // 논리 삭제
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void updateVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
        this.isSynced = false;
        this.ttsJobId = null;
        this.ttsPollCount = 0;
    }

    public void updateTtsJobId(String ttsJobId) {
        this.ttsJobId = ttsJobId;
        this.ttsPollCount = 0;
        this.lastTtsPolledAt = LocalDateTime.now();
    }

    public void incrementPollCount() {
        this.ttsPollCount = (this.ttsPollCount == null) ? 1 : this.ttsPollCount + 1;
        this.lastTtsPolledAt = LocalDateTime.now();
    }
}