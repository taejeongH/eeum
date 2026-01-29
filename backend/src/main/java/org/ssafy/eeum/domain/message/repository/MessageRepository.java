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
}
