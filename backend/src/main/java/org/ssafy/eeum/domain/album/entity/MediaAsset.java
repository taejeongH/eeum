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

    @Builder
    public MediaAsset(Family family, User uploader, String storageUrl, LocalDate takenAt, String description) {
        this.family = family;
        this.uploader = uploader;
        this.storageUrl = storageUrl;
        this.takenAt = takenAt;
        this.description = description;
        this.isSynced = false; // 생성 시에는 미동기화 상태
    }

    public void update(LocalDate takenAt, String description) {
        this.takenAt = takenAt;
        this.description = description;
        this.isSynced = false; // 수정 시 다시 동기화 필요 상태로 변경
    }

    public void markSynced() {
        this.isSynced = true;
    }
}
