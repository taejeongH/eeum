package org.ssafy.eeum.domain.family.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.family.entity.Family;

import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {
    Optional<Family> findByInviteCode(String inviteCode);
}
