package org.ssafy.eeum.global.auth.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.SocialAccountRepository;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.auth.entity.SocialAccount;

import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OAuth2 로그인을 처리하는 서비스 클래스입니다.
 * 제공자(카카오 등)로부터 전달받은 사용자 정보를 바탕으로 회원 가입 및 정보 업데이트를 수행합니다.
 * 
 * @summary OAuth2 사용자 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final S3Service s3Service;

    /**
     * OAuth2 제공자로부터 사용자 정보를 로드하고 후처리합니다.
     * 
     * @summary OAuth2 사용자 로드 및 처리
     * @param userRequest OAuth2 사용자 요청 정보
     * @return 로드된 OAuth2User 객체
     * @throws OAuth2AuthenticationException 제공자가 지원되지 않거나 처리 중 오류 발생 시
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 로그인 시도: {}", registrationId);

        if ("kakao".equals(registrationId)) {
            return processKakaoUser(oAuth2User);
        }

        throw new OAuth2AuthenticationException("지원하지 않는 로그인 제공자입니다: " + registrationId);
    }

    /**
     * 카카오 사용자 정보를 처리합니다. 기존 회원이면 정보를 업데이트하고, 신규 회원이면 가입 처리를 합니다.
     * 
     * @summary 카카오 사용자 처리
     * @param oAuth2User 카카오에서 로드된 사용자 정보
     * @return CustomUserDetails 객체
     */
    private OAuth2User processKakaoUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String kakaoId = attributes.get("id").toString();

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String email = getEmail(kakaoAccount, kakaoId);
        String nickname = profile.get("nickname").toString();
        String kakaoProfileUrl = profile.get("profile_image_url") != null ? profile.get("profile_image_url").toString()
                : null;

        SocialAccount socialAccount = socialAccountRepository.findByProviderAndProviderUserId("KAKAO", kakaoId)
                .orElse(null);

        User user;
        if (socialAccount != null) {
            user = handleExistingUser(socialAccount, nickname, email, kakaoProfileUrl);
        } else {
            user = handleNewUser(nickname, email, kakaoProfileUrl, kakaoId);
        }

        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }

    private String getEmail(Map<String, Object> kakaoAccount, String kakaoId) {
        String email = kakaoAccount.get("email") != null ? kakaoAccount.get("email").toString() : null;
        return (email == null || email.isBlank()) ? "kakao_" + kakaoId + "@social.eeum" : email;
    }

    private User handleExistingUser(SocialAccount socialAccount, String nickname, String email,
            String kakaoProfileUrl) {
        User user = socialAccount.getUser();
        String currentProfileImage = user.getProfileImage();

        if (shouldMigrateProfileImage(currentProfileImage, kakaoProfileUrl)) {
            log.info("기존 사용자(ID: {})의 프로필 이미지를 S3로 마이그레이션합니다.", user.getId());
            String newS3Key = s3Service.uploadImageFromUrl(kakaoProfileUrl, "profile");
            if (newS3Key != null) {
                currentProfileImage = newS3Key;
            }
        }

        user.updateFromKakao(nickname, email, currentProfileImage);
        userRepository.save(user);
        log.info("기존 사용자 정보 업데이트 완로: {}", user.getId());
        return user;
    }

    private User handleNewUser(String nickname, String email, String kakaoProfileUrl, String kakaoId) {
        log.info("신규 사용자 감지. 카카오 프로필 처리 중...");
        String profileImage = null;

        if (kakaoProfileUrl != null) {
            profileImage = s3Service.uploadImageFromUrl(kakaoProfileUrl, "profile");
            if (profileImage != null) {
                log.info("신규 사용자의 카카오 이미지를 S3에 성공적으로 업로드했습니다. Key: {}", profileImage);
            } else {
                log.error("신규 사용자의 카카오 이미지를 S3에 업로드하는 데 실패했습니다.");
            }
        }

        User user = User.builder()
                .name(nickname)
                .email(email)
                .profileImage(profileImage)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        socialAccountRepository.save(SocialAccount.createKakaoAccount(user, kakaoId));
        log.info("신규 사용중 가입 완료. ID: {}", user.getId());
        return user;
    }

    private boolean shouldMigrateProfileImage(String currentImage, String kakaoUrl) {
        return (currentImage == null || currentImage.startsWith("http")) && kakaoUrl != null;
    }
}