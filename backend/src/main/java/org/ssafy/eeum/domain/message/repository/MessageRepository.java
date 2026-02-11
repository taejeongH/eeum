package org.ssafy.eeum.domain.message.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.voice.entity.VoiceTask;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {

    List<Message> findAllByGroupAndDeletedAtIsNullOrderByCreatedAtAsc(Family group);

    Optional<Message> findByIdAndDeletedAtIsNull(Integer id);

    List<Message> findAllByGroupIdAndIsSyncedFalse(Integer groupId);

    @Modifying
    @Query("UPDATE Message m SET m.isSynced = true WHERE m.id IN :ids")
    void markAsSynced(@Param("ids") List<Integer> ids);

    Optional<Message> findByVoiceTask(VoiceTask voiceTask);
}
