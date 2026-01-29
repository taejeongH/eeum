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

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Transactional
    public ProfileResponseDto updateProfile(String id, ProfileRequestDto profileRequest, MultipartFile file) {
        User user = userRepository.findById(Integer.parseInt(id))
                .orElseThrow(() -> new NoSuchElementException("User not found"));

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
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        String imageKey = user.getProfileImage();
        String presignedUrl = s3Service.getPresignedUrl(imageKey);

        ProfileResponseDto responseDto = ProfileResponseDto.of(user);
        responseDto.setProfileImage(presignedUrl);
        return responseDto;
    }
}
