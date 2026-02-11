package org.ssafy.eeum.domain.album.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDate;

/**
 * 가족 앨범의 개별 사진 및 미디어 자산을 나타내는 엔티티입니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "media_assets")
public class MediaAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_user_id", nullable = false)
    private User uploader;

    @Column(name = "storage_url", nullable = false, columnDefinition = "TEXT")
    private String storageUrl;

    @Column(name = "taken_at")
    private LocalDate takenAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_synced", nullable = false)
    private Boolean isSynced;

    /**
     * MediaAsset 객체를 생성합니다.
     * 
     * @summary 미디어 자산 생성
     * @param family      소속 가족
     * @param uploader    업로드한 사용자
     * @param storageUrl  S3 저장 경로
     * @param takenAt     사진 촬영 날짜
     * @param description 사진 설명
     */
    @Builder
    public MediaAsset(Family family, User uploader, String storageUrl, LocalDate takenAt, String description) {
        this.family = family;
        this.uploader = uploader;
        this.storageUrl = storageUrl;
        this.takenAt = takenAt;
        this.description = description;
        this.isSynced = false;
    }

    /**
     * 사진의 촬영 날짜와 설명을 업데이트합니다.
     * 
     * @summary 사진 정보 수정
     * @param takenAt     수정할 촬영 날짜
     * @param description 수정할 설명
     */
    public void update(LocalDate takenAt, String description) {
        this.takenAt = takenAt;
        this.description = description;
        this.isSynced = false;
    }

    /**
     * 해당 자산이 IoT 기기와 동기화되었음을 표시합니다.
     * 
     * @summary 동기화 완료 표시
     */
    public void markSynced() {
        this.isSynced = true;
    }
}
