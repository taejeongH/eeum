package org.ssafy.eeum.domain.health.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.ssafy.eeum.domain.iot.entity.FallEvent;

import java.time.LocalDateTime;

@Entity
@Table(name = "heart_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HeartRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_rate", nullable = false)
    private int minRate;

    @Column(name = "max_rate", nullable = false)
    private int maxRate;

    @Column(name = "avg_rate", nullable = false)
    private int avgRate;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private org.ssafy.eeum.domain.family.entity.Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_id", nullable = true)
    private FallEvent fallEvent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
