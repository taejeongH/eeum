package org.ssafy.eeum.domain.users.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.users.dto.ProfileRequestDto;
import org.ssafy.eeum.domain.users.dto.ProfileResponseDto;
import org.ssafy.eeum.global.infra.s3.S3Service;

import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final org.ssafy.eeum.global.infra.fcm.FcmService fcmService;

    @Transactional
    public ProfileResponseDto updateProfile(String id, ProfileRequestDto profileRequest, MultipartFile file) {
        User user = userRepository.findById(Integer.parseInt(id))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String imageKey = user.getProfileImage();

        if (file != null && !file.isEmpty()) {
            // 기존 이미지 삭제
            if (imageKey != null && !imageKey.isEmpty()) {
                s3Service.deleteFile(imageKey);
            }
            // 새 이미지 업로드
            imageKey = s3Service.uploadFile(file, "profile");
        }

        user.updateProfile(
                profileRequest.getName(),
                profileRequest.getPhone(),
                profileRequest.getBirthDate(),
                profileRequest.getGender(),
                profileRequest.getAddress(),
                imageKey);

        // presigned URL을 생성해서 응답 DTO에 담아 반환
        String presignedUrl = s3Service.getPresignedUrl(imageKey);
        ProfileResponseDto responseDto = ProfileResponseDto.of(user);
        responseDto.setProfileImage(presignedUrl);
        return responseDto;
    }

    @Transactional(readOnly = true)
    public ProfileResponseDto getProfile(String id) {
        User user = userRepository.findById(Integer.parseInt(id))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String imageKey = user.getProfileImage();
        String presignedUrl = s3Service.getPresignedUrl(imageKey);

        ProfileResponseDto responseDto = ProfileResponseDto.of(user);
        responseDto.setProfileImage(presignedUrl);
        return responseDto;
    }

    @Transactional
    public void updateFcmToken(String id, String fcmToken) {
        User user = userRepository.findById(Integer.parseInt(id))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(fcmToken);
    }

    public String sendTestMessage(String userId, String title, String body, String type) {
        User user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        
        String token = user.getFcmToken();
        if (token == null || token.isEmpty()) {
            return "FCM Token not found for user";
        }

        String finalTitle = title != null && !title.isEmpty() ? title : "테스트 알림 🔔";
        String finalBody = body != null && !body.isEmpty() ? body : "이 메시지가 보이면 FCM이 정상 동작하는 것입니다!";
        String finalType = type != null && !type.trim().isEmpty() ? type.trim() : "NORMAL";

        fcmService.sendMessageTo(token, finalTitle, finalBody, finalType, null, null, null, null, null);
        return "Message sent to " + user.getName();
    }
}
