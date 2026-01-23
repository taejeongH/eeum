package org.ssafy.eeum.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ssafy.eeum.domain.user.entity.SocialAccount;
import java.util.Optional;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Integer> {
    // 카카오 ID로 소셜 계정 찾기
    Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}