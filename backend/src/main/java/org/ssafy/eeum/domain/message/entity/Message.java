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

    @Builder.Default
    @Column(name = "is_synced", columnDefinition = "TINYINT", nullable = false)
    private Boolean isSynced = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @Column(name = "is_read", columnDefinition = "TINYINT")
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * 로직 메서드
     */
    
    // 목소리 URL 업데이트 시 동기화 상태를 false로 변경
    public void updateVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
        this.isSynced = false;
    }

    // 동기화 완료 표시
    public void markSynced() {
        this.isSynced = true;
    }

    // 읽음 처리
    public void markRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    // 논리 삭제
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}