package org.ssafy.eeum.domain.message.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.message.entity.Message;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {

    List<Message> findAllByGroupAndDeletedAtIsNullOrderByCreatedAtAsc(Family group);

    Optional<Message> findByIdAndDeletedAtIsNull(Integer id);

    List<Message> findAllByGroupIdAndIsSyncedFalse(Integer groupId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Message m SET m.isSynced = true WHERE m.id IN :ids")
    void markAsSynced(@org.springframework.data.repository.query.Param("ids") List<Integer> ids);
}
