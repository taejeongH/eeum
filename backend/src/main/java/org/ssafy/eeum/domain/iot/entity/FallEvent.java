package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.auth.entity.User;
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "severity", nullable = false)
    private Integer severity;

    @Column(name = "video_path", length = 255)
    private String videoPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_type", nullable = false)
    private StatusType statusType;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    private LocalDateTime resolvedAt;

    public enum StatusType {
        DETECTED, CONFIRMED, FALSE_ALARM, RESOLVED
    }

    public void updateSeverity(Integer severity) {
        this.severity = severity;
    }
}