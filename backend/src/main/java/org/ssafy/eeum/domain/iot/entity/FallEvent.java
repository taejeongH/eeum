package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 낙상 감지 이벤트 정보를 저장하고 관리하는 엔티티 클래스입니다.
 * 심각도(Severity), 분석 상태(StatusType), 영상 정보(VideoPath) 및 STT 분석 내용을 관리합니다.
 * 
 * @summary 낙상 이벤트 엔티티
 */
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

    /**
     * 위급 상황 정도 (1: 주의, 2: 위급 등)
     */
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

    /**
     * 음성 인식 대화 내용 및 사유
     */
    @Column(name = "stt_content", columnDefinition = "TEXT")
    private String sttContent;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * AI 분석 확신도
     */
    @Column(name = "confidence")
    private Double confidence;

    /**
     * 낙상 이벤트의 처리 상태 타입 정의
     */
    public enum StatusType {
        UNDER_REVIEW, EMERGENCY, SAFE, RESOLVED
    }

    /**
     * 낙상 영상의 처리 상태 타입 정의
     */
    public enum VideoStatus {
        NONE, PENDING, SUCCESS
    }

    /**
     * 영상 경로를 설정합니다.
     * 
     * @summary 영상 경로 설정
     * @param videoPath S3 영상 경로
     */
    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    /**
     * 영상 업로드/처리 상태를 업데이트합니다.
     * 
     * @summary 영상 상태 업데이트
     * @param videoStatus 새로운 영상 상태
     */
    public void updateVideoStatus(VideoStatus videoStatus) {
        this.videoStatus = videoStatus;
    }

    /**
     * 이벤트 상태를 '위급(EMERGENCY)' 상황으로 변경하고 사유를 기록합니다.
     * 
     * @summary 위급 상황으로 전환
     * @param sttContent 위급 상황 판단 사유(음성 텍스트 등)
     */
    public void updateToEmergency(String sttContent) {
        this.severity = 2;
        this.statusType = StatusType.EMERGENCY;
        this.sttContent = sttContent;
    }

    /**
     * 이벤트 상태를 '안전(SAFE)' 상황으로 변경하고 완료 시간을 기록합니다.
     * 
     * @summary 안전 상황으로 전환
     * @param sttContent 안전 판단 근교(음성 텍스트 등)
     */
    public void updateToSafe(String sttContent) {
        this.statusType = StatusType.SAFE;
        this.sttContent = sttContent;
        this.resolvedAt = LocalDateTime.now();
    }
}