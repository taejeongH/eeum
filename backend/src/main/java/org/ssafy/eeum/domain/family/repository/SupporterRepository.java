package org.ssafy.eeum.domain.family.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.entity.SupporterId;
import org.ssafy.eeum.domain.user.entity.User;

import java.util.List;
import java.util.Optional;

public interface SupporterRepository extends JpaRepository<Supporter, SupporterId> {
    List<Supporter> findAllByUser(User user);
    List<Supporter> findAllByFamily(Family family);
    Optional<Supporter> findByUserAndFamily(User user, Family family);
    Optional<Supporter> findByFamilyAndRole(Family family, Supporter.Role role);
}
