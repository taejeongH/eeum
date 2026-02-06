package org.ssafy.eeum.domain.album.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.album.entity.MediaAsset;

import java.util.List;

public interface AlbumRepository extends JpaRepository<MediaAsset, Integer> {

    @Query("SELECT m FROM MediaAsset m JOIN FETCH m.uploader WHERE m.family.id = :familyId")
    List<MediaAsset> findAllByFamilyId(@Param("familyId") Integer familyId);

    List<MediaAsset> findAllByFamilyIdAndIsSyncedFalse(Integer familyId);

    @Modifying
    @Transactional
    @Query("UPDATE MediaAsset m SET m.isSynced = true WHERE m.id IN :ids")
    void markAsSynced(@Param("ids") List<Integer> ids);
}
