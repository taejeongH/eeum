package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "fall_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FallEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @Column(name = "severity", nullable = false)
    private Integer severity;

    @Column(name = "video_path", length = 255)
    private String videoPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_type", nullable = false)
    private StatusType statusType;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", nullable = false)
    @Builder.Default
    private VideoStatus videoStatus = VideoStatus.NONE;

    @Column(name = "stt_content", columnDefinition = "TEXT")
    private String sttContent;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "confidence")
    private Double confidence;

    public enum StatusType {
        UNDER_REVIEW, EMERGENCY, SAFE, RESOLVED
    }

    public enum VideoStatus {
        NONE, PENDING, SUCCESS
    }

    public void updateVideoStatus(VideoStatus videoStatus) {
        this.videoStatus = videoStatus;
    }

    public void updateToEmergency(String sttContent) {
        this.severity = 2;
        this.statusType = StatusType.EMERGENCY;
        this.sttContent = sttContent;
    }

    public void updateToSafe(String sttContent) {
        this.statusType = StatusType.SAFE;
        this.sttContent = sttContent;
        this.resolvedAt = LocalDateTime.now();
    }
}