package org.ssafy.eeum.domain.health.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.iot.entity.FallEvent;

import java.time.LocalDateTime;

/**
 * 사용자의 심박수 측정 정보를 관리하는 엔티티 클래스입니다.
 * 최저, 최고, 평균 심박수와 측정 시각을 기록하며, 특정 낙상 이벤트와 연관될 수 있습니다.
 * 
 * @summary 심박수 엔티티
 */
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
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_id", nullable = true)
    private FallEvent fallEvent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
