package org.ssafy.eeum.domain.family.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.dto.*;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.schedule.service.ScheduleService;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 가족 그룹과 관련된 데이터 처리 및 비즈니스 로직을 담당하는 서비스 클래스입니다.
 * 가족 생성, 가입, 상세 정보 조회, 멤버 관리 및 초대코드 생성 등을 처리합니다.
 * 
 * @summary 가족 관리 서비스
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

        private final FamilyRepository familyRepository;
        private final UserRepository userRepository;
        private final SupporterRepository supporterRepository;
        private final S3Service s3Service;
        private final IotDeviceRepository iotDeviceRepository;
        private final ScheduleService scheduleService;
        private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private static final int CODE_LENGTH = 8;
        private static final SecureRandom RANDOM = new SecureRandom();

        // ... existing methods ...

        /**
         * 특정 가족 그룹의 상세 정보를 조회합니다.
         * 가족 멤버 리스트, 보호 대상자, 대표자 정보 및 IoT 기기 기반의 스트리밍 경로를 포함합니다.
         * 
         * @summary 가족 그룹 상세 정보 조회
         * @param familyId 가족 그룹 식별자
         * @return 가족 상세 정보 응답 DTO
         */
        @Transactional(readOnly = true)
        public FamilyDetailResponseDto getFamilyDetails(Integer familyId) {
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                List<Supporter> supporters = supporterRepository.findAllByFamily(family);

                Integer dependentUserId = supporters.stream()
                                .filter(s -> s.getRole() == Supporter.Role.PATIENT)
                                .map(s -> s.getUser().getId())
                                .findFirst()
                                .orElse(null);

                List<FamilyMemberPriorityDto> memberPriorities = supporters.stream()
                                .filter(s -> s.getRole() == Supporter.Role.CAREGIVER)
                                .map(s -> FamilyMemberPriorityDto.builder()
                                                .userId(s.getUser().getId())
                                                .emergencyPriority(s.getEmergencyPriority())
                                                .build())
                                .collect(Collectors.toList());

                List<FamilyMemberDto> members = supporters.stream()
                                .map(supporter -> {
                                        FamilyMemberDto familyMemberDto = FamilyMemberDto.of(supporter);
                                        String presignedUrl = s3Service
                                                        .getPresignedUrl(supporter.getUser().getProfileImage());
                                        familyMemberDto.setProfileImage(presignedUrl);
                                        return familyMemberDto;
                                })
                                .collect(Collectors.toList());

                String streamingTarget = family.getStreamingUrl();
                List<IotDevice> devices = iotDeviceRepository
                                .findAllByFamilyId(familyId);

                for (IotDevice device : devices) {
                        if ("JETSON".equalsIgnoreCase(device.getDeviceType())) {
                                String sn = device.getSerialNumber();
                                if (sn != null && sn.length() > 5 && sn.toUpperCase().startsWith("EEUM")) {
                                        streamingTarget = sn;
                                        break;
                                } else if (streamingTarget == null || streamingTarget.length() < 5) {
                                        streamingTarget = sn;
                                }
                        }
                }

                return FamilyDetailResponseDto.builder()
                                .familyId(family.getId())
                                .groupName(family.getGroupName())
                                .dependentUserId(dependentUserId)
                                .memberPriorities(memberPriorities)
                                .members(members)
                                .streamingUrl(streamingTarget)
                                .build();
        }

        /**
         * 새로운 가족 그룹을 생성합니다.
         * 생성한 사용자는 해당 그룹의 대표자(방장)가 되며, 고유한 초대코드를 발급받습니다.
         * 
         * @summary 신규 가족 그룹 생성
         * @param userId                 생성 요청자 식별자
         * @param createFamilyRequestDto 가족 생성 정보 DTO
         * @return 생성된 가족 정보 결과 DTO
         */
        @Transactional
        public CreateFamilyResponseDto createFamily(String userId, CreateFamilyRequestDto createFamilyRequestDto) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                String inviteCode;
                do {
                        inviteCode = generateInviteCode();
                } while (familyRepository.findByInviteCode(inviteCode).isPresent());

                Family family = Family.builder()
                                .groupName(createFamilyRequestDto.getName())
                                .inviteCode(inviteCode)
                                .user(user)
                                .build();
                Family savedFamily = familyRepository.save(family);

                Supporter supporter = Supporter.builder()
                                .user(user)
                                .family(savedFamily)
                                .role(Supporter.Role.CAREGIVER)
                                .representativeFlag(true)
                                .relationship(createFamilyRequestDto.getRelationship())
                                .build();
                supporterRepository.save(supporter);

                scheduleService.addBirthdaySchedule(user, savedFamily);

                return CreateFamilyResponseDto.of(savedFamily);
        }

        /**
         * 현재 로그인한 사용자가 속한 모든 가족 그룹 목록을 조회합니다.
         * 각 그룹에서의 역할 및 보호 대상자 정보를 포함합니다.
         * 
         * @summary 가입된 가족 그룹 목록 조회
         * @param userId 사용자 식별자
         * @return 소속 가족 그룹 간략 정보 리스트
         */
        @Transactional(readOnly = true)
        public List<FamilySimpleResponseDto> findMyFamilies(String userId) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                List<Supporter> supporters = supporterRepository.findAllByUser(user);

                return supporters.stream()
                                .map(supporter -> {
                                        String rel = supporter.getRelationship();

                                        if (rel == null || rel.trim().isEmpty()) {
                                                rel = supporterRepository.findAllByFamily(supporter.getFamily())
                                                                .stream()
                                                                .map(Supporter::getRelationship)
                                                                .filter(r -> r != null && !r.trim().isEmpty())
                                                                .findFirst()
                                                                .orElse(null);
                                        }

                                        String dependentName = supporterRepository
                                                        .findAllByFamily(supporter.getFamily())
                                                        .stream()
                                                        .filter(s -> s.getRole() == Supporter.Role.PATIENT)
                                                        .map(s -> s.getUser().getName())
                                                        .findFirst()
                                                        .orElse(null);

                                        return FamilySimpleResponseDto.of(supporter.getFamily(),
                                                        supporter.isRepresentativeFlag(), rel, dependentName);
                                })
                                .collect(Collectors.toList());
        }

        /**
         * 가족 그룹에 속한 모든 멤버 리스트(프로필 이미지 포함)를 상세 조회합니다.
         * 
         * @summary 가족 멤버 목록 상세 조회
         * @param userId   요청자 식별자
         * @param familyId 가족 그룹 식별자
         * @return 가족 멤버 DTO 리스트
         */
        @Transactional(readOnly = true)
        public List<FamilyMemberDto> getFamilyMembers(String userId, Integer familyId) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
                verifyFamilyMember(user, family);

                List<Supporter> supporters = supporterRepository.findAllByFamily(family);

                return supporters.stream()
                                .map(supporter -> {
                                        String presignedUrl = s3Service
                                                        .getPresignedUrl(supporter.getUser().getProfileImage());
                                        FamilyMemberDto familyMemberDto = FamilyMemberDto.of(supporter);

                                        familyMemberDto.setProfileImage(presignedUrl);
                                        return familyMemberDto;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * 특정 가족 멤버의 상세 프로필 정보를 조회합니다.
         * 
         * @summary 개별 가족 멤버 상세 조회
         * @param userId       요청자 식별자
         * @param familyId     가족 그룹 식별자
         * @param memberUserId 조회 대상 멤버 식별자
         * @return 멤버 상세 정보 응답 DTO
         */
        @Transactional(readOnly = true)
        public FamilyMemberDetailResponseDto getFamilyMemberDetails(String userId, Integer familyId,
                        Integer memberUserId) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
                verifyFamilyMember(user, family);

                User memberUser = userRepository.findById(memberUserId)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Supporter supporter = supporterRepository.findByUserAndFamily(memberUser, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));

                FamilyMemberDetailResponseDto responseDto = FamilyMemberDetailResponseDto.of(memberUser, supporter);

                String imageKey = memberUser.getProfileImage();
                String presignedUrl = s3Service.getPresignedUrl(imageKey);
                responseDto.setProfileImage(presignedUrl);
                responseDto.setCurrentUserOwner(user.getId().equals(family.getUser().getId()));

                return responseDto;
        }

        /**
         * 초대코드를 통해 해당 가족 그룹의 이름과 초대자 정보를 확인합니다.
         * 가입 전에 정보를 미리 보여주는 용도로 사용됩니다.
         * 
         * @summary 초대 코드 정보 확인
         * @param inviteCode 초대 코드
         * @return 초대 정보 응답 DTO
         */
        @Transactional(readOnly = true)
        public InviteInfoResponseDto getInviteInfo(String inviteCode) {
                Family family = familyRepository.findByInviteCode(inviteCode)
                                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

                String inviterName = family.getUser().getName();
                String groupName = family.getGroupName();

                return InviteInfoResponseDto.builder()
                                .groupName(groupName)
                                .inviterName(inviterName)
                                .build();
        }

        @Transactional(readOnly = true)
        public JoinPreviewResponseDto getFamilyJoinPreview(String inviteCode) {
                Family family = familyRepository.findByInviteCode(inviteCode)
                                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

                String familyName = family.getGroupName();
                String inviterName = family.getUser().getName();

                return JoinPreviewResponseDto.builder()
                                .familyName(familyName)
                                .inviterName(inviterName)
                                .build();
        }

        private void verifyFamilyMember(User user, Family family) {
                supporterRepository.findByUserAndFamily(user, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));
        }

        private String generateInviteCode() {
                StringBuilder code = new StringBuilder(CODE_LENGTH);
                for (int i = 0; i < CODE_LENGTH; i++) {
                        code.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
                }
                return code.toString();
        }

        /**
         * 가족 그룹에서 탈퇴하거나, 대표자인 경우 그룹 자체를 해체합니다.
         * 
         * @summary 가족 그룹 탈퇴 및 해체
         * @param userId   사용자 식별자
         * @param familyId 가족 그룹 식별자
         * @return 탈퇴 후 상태 정보 응답 DTO
         */
        @Transactional
        public LeaveFamilyResponseDto leaveFamily(String userId, Integer familyId) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                if (family.getUser().getId().equals(user.getId())) {
                        familyRepository.delete(family);
                } else {
                        Supporter supporter = supporterRepository.findByUserAndFamily(user, family)
                                        .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));
                        supporterRepository.delete(supporter);
                }

                List<FamilySimpleResponseDto> remainingFamilies = findMyFamilies(userId);
                if (!remainingFamilies.isEmpty()) {
                        return LeaveFamilyResponseDto.builder()
                                        .nextFamilyId(remainingFamilies.get(0).getId())
                                        .nextFamilyName(remainingFamilies.get(0).getName())
                                        .message("가족 그룹에서 탈퇴/삭제되었습니다. 남아있는 다른 가족 그룹입니다.")
                                        .build();
                } else {
                        return LeaveFamilyResponseDto.builder()
                                        .message("모든 가족 그룹에서 탈퇴/삭제되었습니다. 더 이상 가입된 가족 그룹이 없습니다.")
                                        .build();
                }
        }

        /**
         * 가족 그룹의 정보를 수정합니다.
         * 그룹명 변경, 보호 대상자 지정/해제, 비상 연락 우선순위 변경 등을 처리합니다.
         * 대표자(방장) 권한이 필요합니다.
         * 
         * @summary 가족 그룹 설정 정보 업데이트
         * @param authenticatedUserId 요청자(대표자) 식별자
         * @param familyId            가족 그룹 식별자
         * @param requestDto          수정할 정보 DTO
         * @return 수정 결과 응답 DTO
         */
        @Transactional
        public UpdateFamilyResponseDto updateFamily(String authenticatedUserId, Integer familyId,
                        UpdateFamilyRequestDto requestDto) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                if (!family.getUser().getId().equals(authenticatedUser.getId())) {
                        throw new CustomException(ErrorCode.NOT_FAMILY_REPRESENTATIVE);
                }

                if (requestDto.getNewGroupName() != null && !requestDto.getNewGroupName().trim().isEmpty()) {
                        family.updateGroupName(requestDto.getNewGroupName());
                        familyRepository.save(family);
                }

                if (requestDto.getDependentUserId() != null) {
                        User dependentUser = userRepository.findById(requestDto.getDependentUserId())
                                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                        Optional<Supporter> existingPatientSupporter = supporterRepository.findByFamilyAndRole(family,
                                        Supporter.Role.PATIENT);

                        if (existingPatientSupporter.isPresent()) {
                                Supporter currentPatient = existingPatientSupporter.get();
                                if (!currentPatient.getUser().getId().equals(dependentUser.getId())
                                                || requestDto.getDependentUserId() == 0) {
                                        supporterRepository.delete(currentPatient);
                                }
                        }

                        if (requestDto.getDependentUserId() != 0) {
                                Supporter newPatientSupporter = Supporter.builder()
                                                .user(dependentUser)
                                                .family(family)
                                                .role(Supporter.Role.PATIENT)
                                                .representativeFlag(false)
                                                .relationship(null)
                                                .build();
                                supporterRepository.save(newPatientSupporter);

                                dependentUser.updateHealthInfo(requestDto.getDependentBloodType(),
                                                requestDto.getDependentChronicDiseases());
                                userRepository.save(dependentUser);
                        }
                } else if (requestDto.getDependentUserId() != null && requestDto.getDependentUserId() == 0) {
                        Optional<Supporter> existingPatientSupporter = supporterRepository.findByFamilyAndRole(family,
                                        Supporter.Role.PATIENT);
                        existingPatientSupporter.ifPresent(supporterRepository::delete);
                }

                if (requestDto.getMemberPriorities() != null && !requestDto.getMemberPriorities().isEmpty()) {
                        for (FamilyMemberPriorityDto priorityDto : requestDto.getMemberPriorities()) {
                                User memberUser = userRepository.findById(priorityDto.getUserId())
                                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                                Supporter memberSupporter = supporterRepository.findByUserAndFamily(memberUser, family)
                                                .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));

                                if (priorityDto.getEmergencyPriority() < 1 || priorityDto.getEmergencyPriority() > 4) {
                                        throw new CustomException(ErrorCode.INVALID_EMERGENCY_PRIORITY);
                                }

                                memberSupporter.updateEmergencyPriority(priorityDto.getEmergencyPriority());
                                supporterRepository.save(memberSupporter);
                        }
                }
                return UpdateFamilyResponseDto.builder()
                                .familyId(family.getId())
                                .familyName(family.getGroupName())
                                .build();
        }

        @Transactional(readOnly = true)
        public String getInviteCode(String authenticatedUserId, Integer familyId) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                // 인증된 유저가 가족의 대표자인지 확인
                if (!family.getUser().getId().equals(authenticatedUser.getId())) {
                        throw new CustomException(ErrorCode.NOT_FAMILY_REPRESENTATIVE);
                }
                return family.getInviteCode();
        }

        /**
         * 가족 초대 코드를 재발급합니다.
         * 기존 코드는 더 이상 사용할 수 없게 되며, 보안을 위해 사용됩니다.
         * 
         * @summary 초대 코드 재발급
         * @param authenticatedUserId 요청자(대표자) 식별자
         * @param familyId            가족 그룹 식별자
         * @return 새로 발급된 초대 코드
         */
        @Transactional
        public String regenerateInviteCode(String authenticatedUserId, Integer familyId) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                // 인증된 유저가 가족의 대표자인지 확인
                if (!family.getUser().getId().equals(authenticatedUser.getId())) {
                        throw new CustomException(ErrorCode.NOT_FAMILY_REPRESENTATIVE);
                }

                String newInviteCode;
                do {
                        newInviteCode = generateInviteCode();
                } while (familyRepository.findByInviteCode(newInviteCode).isPresent());

                family.updateInviteCode(newInviteCode);
                familyRepository.save(family);

                return newInviteCode;
        }

        /**
         * 대표자 권한으로 특정 멤버를 가족 그룹에서 강제 제외합니다.
         * 
         * @summary 가족 멤버 강제 퇴장 처리
         * @param authenticatedUserId 요청자(대표자) 식별자
         * @param familyId            가족 그룹 식별자
         * @param memberUserId        내보낼 멤버 식별자
         */
        @Transactional
        public void deleteFamilyMember(String authenticatedUserId, Integer familyId, Integer memberUserId) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                // 인증된 유저가 가족의 대표자인지 확인
                if (!family.getUser().getId().equals(authenticatedUser.getId())) {
                        throw new CustomException(ErrorCode.NOT_FAMILY_REPRESENTATIVE);
                }

                User memberUserToDelete = userRepository.findById(memberUserId.intValue())
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                // 대표자가 자기 자신을 삭제하는 경우
                if (authenticatedUser.getId().equals(memberUserToDelete.getId())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "자기 자신을 삭제하려면 가족 탈퇴 API를 사용해주세요.");
                }

                Supporter supporterToDelete = supporterRepository.findByUserAndFamily(memberUserToDelete, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));

                supporterRepository.delete(supporterToDelete);
        }

        /**
         * 초대코드를 사용하여 특정 가족 그룹에 가입합니다.
         * 
         * @summary 가족 그룹 가입
         * @param authenticatedUserId 요청자 식별자
         * @param inviteCode          초대 코드
         * @return 가입 완료된 가족의 간략 정보 DTO
         */
        @Transactional
        public FamilySimpleResponseDto joinFamily(String authenticatedUserId, String inviteCode) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findByInviteCode(inviteCode)
                                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

                // 이미 멤버인지 확인
                if (supporterRepository.findByUserAndFamily(authenticatedUser, family).isPresent()) {
                        throw new CustomException(ErrorCode.ALREADY_FAMILY_MEMBER);
                }

                // 이미 대표자인지 확인
                if (family.getUser().getId().equals(authenticatedUser.getId())) {
                        throw new CustomException(ErrorCode.ALREADY_FAMILY_REPRESENTATIVE);
                }

                Supporter newSupporter = Supporter.builder()
                                .user(authenticatedUser)
                                .family(family)
                                .role(Supporter.Role.CAREGIVER)
                                .representativeFlag(false)
                                .relationship(null)
                                .build();
                supporterRepository.save(newSupporter);

                // Add birthday schedule
                scheduleService.addBirthdaySchedule(authenticatedUser, family);

                return FamilySimpleResponseDto.of(family);
        }

        /**
         * 현재 로그인한 사용자가 해당 가족 그룹 내에서의 호칭/관계 정보를 업데이트합니다.
         * 
         * @summary 나의 가족 관계 정보 수정
         * @param authenticatedUserId 사용자 식별자
         * @param familyId            가족 그룹 식별자
         * @param requestDto          수정할 관계 정보 DTO
         */
        @Transactional
        public void updateMyRelationship(String authenticatedUserId, Integer familyId,
                        UpdateMemberRelationshipRequestDto requestDto) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                Supporter supporter = supporterRepository.findByUserAndFamily(authenticatedUser, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));

                supporter.updateRelationship(requestDto.getRelationship());
                supporterRepository.save(supporter);
        }
}
