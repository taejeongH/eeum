package org.ssafy.eeum.domain.album.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.album.entity.MediaAsset;

import java.util.List;

/**
 * 가족 앨범 미디어 자산에 대한 데이터 접근을 담당하는 리포지토리입니다.
 */
public interface AlbumRepository extends JpaRepository<MediaAsset, Integer> {

    /**
     * 특정 가족의 모든 미디어 자산을 조회합니다. 업로더 정보를 함께 페치 조인합니다.
     * 
     * @summary 가족별 사진 목록 조회
     * @param familyId 가족 식별자
     * @return 미디어 자산 리스트
     */
    @Query("SELECT m FROM MediaAsset m JOIN FETCH m.uploader WHERE m.family.id = :familyId")
    List<MediaAsset> findAllByFamilyId(@Param("familyId") Integer familyId);

    /**
     * 특정 가족의 미동기화된 미디어 자산을 조회합니다.
     * 
     * @summary 미동기화 사진 조회
     * @param familyId 가족 식별자
     * @return 미동기화된 미디어 자산 리스트
     */
    List<MediaAsset> findAllByFamilyIdAndIsSyncedFalse(Integer familyId);

    /**
     * 지정된 ID 목록에 해당하는 미디어 자산들의 동기화 상태를 true로 업데이트합니다.
     * 
     * @summary 동기화 상태 업데이트
     * @param ids 업데이트할 미디어 자산 식별자 목록
     */
    @Modifying
    @Transactional
    @Query("UPDATE MediaAsset m SET m.isSynced = true WHERE m.id IN :ids")
    void markAsSynced(@Param("ids") List<Integer> ids);
}
