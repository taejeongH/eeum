package org.ssafy.eeum.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_accounts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String provider; 

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    public static SocialAccount createKakaoAccount(User user, String providerUserId) {
        return SocialAccount.builder()
                .user(user)
                .provider("KAKAO")
                .providerUserId(providerUserId)
                .connectedAt(LocalDateTime.now())
                .build();
    }
}