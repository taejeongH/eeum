package org.ssafy.eeum.security.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.user.User;
import org.ssafy.eeum.repository.SocialAccountRepository;
import org.ssafy.eeum.repository.UserRepository;
import org.ssafy.eeum.domain.user.SocialAccount;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 Login: {}", registrationId);

        // 카카오 로그인 처리
        if ("kakao".equals(registrationId)) {
            return processKakaoUser(oAuth2User);
        }

        throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
    }

    private OAuth2User processKakaoUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 카카오 사용자 ID
        String kakaoId = attributes.get("id").toString();

        // 카카오 계정 정보
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String email = kakaoAccount.get("email") != null ? kakaoAccount.get("email").toString() : null;
        String nickname = profile.get("nickname").toString();
        String profileImage = profile.get("profile_image_url") != null ?
                profile.get("profile_image_url").toString() : null;

        log.info("Kakao User - ID: {}, Nickname: {}, Email: {}", kakaoId, nickname, email);

        // 기존 소셜 계정 확인
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId("KAKAO", kakaoId)
                .orElse(null);

        User user;
        if (socialAccount != null) {
            // 기존 사용자 - 정보 업데이트
            user = socialAccount.getUser();
            user.updateFromKakao(nickname, email, profileImage);
            userRepository.save(user);
            log.info("Existing user updated: {}", user.getId());
        } else {
            // 신규 사용자 생성
            user = User.builder()
                    .name(nickname)
                    .email(email)
                    .profileImage(profileImage)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            user = userRepository.save(user);

            // 소셜 계정 연동
            socialAccount = SocialAccount.createKakaoAccount(user, kakaoId);
            socialAccountRepository.save(socialAccount);
            log.info("New user created: {}", user.getId());
        }

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }
}