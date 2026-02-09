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

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final S3Service s3Service;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 Login: {}", registrationId);

        
        if ("kakao".equals(registrationId)) {
            return processKakaoUser(oAuth2User);
        }

        throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
    }

    private OAuth2User processKakaoUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        
        String kakaoId = attributes.get("id").toString();

        
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String email = kakaoAccount.get("email") != null ? kakaoAccount.get("email").toString() : null;

        if (email == null || email.isBlank()) {
            email = "kakao_" + kakaoId + "@social.eeum";
        }

        String nickname = profile.get("nickname").toString();
        String kakaoProfileUrl = profile.get("profile_image_url") != null ? profile.get("profile_image_url").toString()
                : null;

        
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId("KAKAO", kakaoId)
                .orElse(null);

        User user;
        if (socialAccount != null) {
            
            user = socialAccount.getUser();

            
            String currentProfileImage = user.getProfileImage();
            if ((currentProfileImage == null || currentProfileImage.startsWith("http")) && kakaoProfileUrl != null) {
                log.info("Migrating profile image to S3 for existing user ID: {}", user.getId());
                String newS3Key = s3Service.uploadImageFromUrl(kakaoProfileUrl, "profile");
                if (newS3Key != null) {
                    currentProfileImage = newS3Key;
                }
            }

            
            user.updateFromKakao(nickname, email, currentProfileImage);
            userRepository.save(user);
            log.info("Existing user updated and migrated: {}", user.getId());
        } else {
            
            log.info("New User Detected. Processing Kakao profile...");
            String profileImage = null;
            if (kakaoProfileUrl != null) {
                log.info("Kakao Profile URL found: {}", kakaoProfileUrl);
                profileImage = s3Service.uploadImageFromUrl(kakaoProfileUrl, "profile");
                if (profileImage != null) {
                    log.info("Successfully uploaded Kakao image to S3 for new user. Key: {}", profileImage);
                } else {
                    log.error("Failed to upload Kakao image to S3 for new user.");
                }
            } else {
                log.info("No Kakao Profile URL found in attributes.");
            }

            user = User.builder()
                    .name(nickname)
                    .email(email)
                    .profileImage(profileImage)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            user = userRepository.save(user);

            
            socialAccount = SocialAccount.createKakaoAccount(user, kakaoId);
            socialAccountRepository.save(socialAccount);
            log.info("New user created with ID: {}", user.getId());
        }

        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }
}