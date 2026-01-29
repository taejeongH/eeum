package org.ssafy.eeum.domain.album.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.album.entity.MediaAsset;

import java.util.List;

public interface AlbumRepository extends JpaRepository<MediaAsset, Integer> {

    // 앱 사용자용: 삭제되지 않은 사진들 조회
    List<MediaAsset> findAllByFamilyIdAndDeletedAtIsNull(Integer familyId);

    // IoT 동기화용: 특정 그룹의 미동기화된 모든 사진 조회 (삭제된 것도 포함하여 알림 대상)
    List<MediaAsset> findAllByFamilyIdAndIsSyncedFalse(Integer familyId);

    // 특정 ID 리스트에 대해 동기화 완료 처리
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE MediaAsset m SET m.isSynced = true WHERE m.id IN :ids")
    void markAsSynced(@Param("ids") List<Integer> ids);
}
