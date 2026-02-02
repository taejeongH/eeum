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

import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FamilyService {

        private final FamilyRepository familyRepository;
        private final UserRepository userRepository;
        private final SupporterRepository supporterRepository;
        private final S3Service s3Service;
        private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private static final int CODE_LENGTH = 8;
        private static final SecureRandom RANDOM = new SecureRandom();

        @Transactional
        public CreateFamilyResponseDto createFamily(String userId, CreateFamilyRequestDto createFamilyRequestDto) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                // 2. 초대 코드 생성 (중복 확인)
                String inviteCode;
                do {
                        inviteCode = generateInviteCode();
                } while (familyRepository.findByInviteCode(inviteCode).isPresent());

                // 3. 가족 생성 및 저장
                Family family = Family.builder()
                                .groupName(createFamilyRequestDto.getName())
                                .inviteCode(inviteCode)
                                .user(user) // 가족을 생성한 유저 설정
                                .build();
                Family savedFamily = familyRepository.save(family);

                // 4. 가족-유저 연결 (Supporter 생성, 생성자는 CAREGIVER 및 대표자로 설정)
                Supporter supporter = Supporter.builder()
                                .user(user)
                                .family(savedFamily)
                                .role(Supporter.Role.CAREGIVER)
                                .representativeFlag(true)
                                .relationship(createFamilyRequestDto.getRelationship())
                                .build();
                supporterRepository.save(supporter);

                // 5. 응답 DTO 반환
                return CreateFamilyResponseDto.of(savedFamily);
        }

        @Transactional(readOnly = true)
        public List<FamilySimpleResponseDto> findMyFamilies(String userId) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                List<Supporter> supporters = supporterRepository.findAllByUser(user);

                return supporters.stream()
                                .map(supporter -> {
                                        String rel = supporter.getRelationship();
                                        // If current user's relationship is null, try to find any relationship in the
                                        // family
                                        if (rel == null || rel.trim().isEmpty()) {
                                                rel = supporterRepository.findAllByFamily(supporter.getFamily())
                                                                .stream()
                                                                .map(Supporter::getRelationship)
                                                                .filter(r -> r != null && !r.trim().isEmpty())
                                                                .findFirst()
                                                                .orElse(null);
                                        }

                                        // Find dependent name in this family
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
                                        FamilyMemberDto familyMemberDto = FamilyMemberDto.builder()
                                                        .userId(supporter.getUser().getId())
                                                        .name(supporter.getUser().getName())
                                                        .isDependent(supporter.getRole() == Supporter.Role.PATIENT)
                                                        .build();
                                        familyMemberDto.setProfileImage(presignedUrl);
                                        return familyMemberDto;
                                })
                                .collect(Collectors.toList());
        }

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

                return FamilyDetailResponseDto.builder()
                                .familyId(family.getId())
                                .groupName(family.getGroupName())
                                .dependentUserId(dependentUserId)
                                .memberPriorities(memberPriorities)
                                .members(members)
                                .build();
        }

        @Transactional(readOnly = true)
        public InviteInfoResponseDto getInviteInfo(String inviteCode) {
                Family family = familyRepository.findByInviteCode(inviteCode)
                                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE)); // Or a more
                                                                                                        // specific
                                                                                                        // error if
                                                                                                        // needed

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

        @Transactional
        public LeaveFamilyResponseDto leaveFamily(String userId, Integer familyId) {
                User user = userRepository.findById(Integer.parseInt(userId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
                // 유저가 가족의 대표자인지 확인
                if (family.getUser().getId().equals(user.getId())) {
                        // 대표자일 경우, 전체 가족 삭제
                        familyRepository.delete(family);
                } else {
                        // 대표자가 아닐 경우, 해당 유저의 서포터 기록 삭제
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

        @Transactional
        public UpdateFamilyResponseDto updateFamily(String authenticatedUserId, Integer familyId,
                        UpdateFamilyRequestDto requestDto) {
                User authenticatedUser = userRepository.findById(Integer.parseInt(authenticatedUserId))
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                // 인증된 유저가 가족의 대표자인지 확인
                if (!family.getUser().getId().equals(authenticatedUser.getId())) {
                        throw new CustomException(ErrorCode.NOT_FAMILY_REPRESENTATIVE);
                }

                // 1. 그룹 이름 수정
                if (requestDto.getNewGroupName() != null && !requestDto.getNewGroupName().trim().isEmpty()) {
                        family.updateGroupName(requestDto.getNewGroupName()); // Assuming updateGroupName method in
                                                                              // Family
                        familyRepository.save(family);
                }

                // 2. 피부양자 설정
                // 3. 피부양자 건강 상태 정보 설정
                if (requestDto.getDependentUserId() != null) {
                        User dependentUser = userRepository.findById(requestDto.getDependentUserId())
                                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                        // 기존 피부양자 Supporter 찾기
                        Optional<Supporter> existingPatientSupporter = supporterRepository.findByFamilyAndRole(family,
                                        Supporter.Role.PATIENT);

                        // 기존 피부양자 삭제 로직
                        if (existingPatientSupporter.isPresent()) {
                                Supporter currentPatient = existingPatientSupporter.get();
                                // 기존 피부양자가 새로운 피부양자와 다른 경우 또는 피부양자를 0으로 설정하여 삭제 요청한 경우
                                if (!currentPatient.getUser().getId().equals(dependentUser.getId())
                                                || requestDto.getDependentUserId() == 0) {
                                        supporterRepository.delete(currentPatient);
                                }
                        }

                        // 새로운 피부양자 설정 (0이 아닌 경우)
                        if (requestDto.getDependentUserId() != 0) {
                                Supporter newPatientSupporter = Supporter.builder()
                                                .user(dependentUser)
                                                .family(family)
                                                .role(Supporter.Role.PATIENT)
                                                .representativeFlag(false) // 피부양자는 대표자가 아님
                                                .relationship(null) // 피부양자의 관계는 여기서 설정하지 않음 (대표자와의 관계는 createFamily에서)
                                                .build();
                                supporterRepository.save(newPatientSupporter);

                                // 피부양자 건강 정보 업데이트
                                dependentUser.updateHealthInfo(requestDto.getDependentBloodType(),
                                                requestDto.getDependentChronicDiseases());
                                userRepository.save(dependentUser);
                        }
                } else if (requestDto.getDependentUserId() != null && requestDto.getDependentUserId() == 0) {
                        // 피부양자 제거 요청
                        Optional<Supporter> existingPatientSupporter = supporterRepository.findByFamilyAndRole(family,
                                        Supporter.Role.PATIENT);
                        existingPatientSupporter.ifPresent(supporterRepository::delete);
                }

                // 4. 멤버별 응급 우선순위 설정
                if (requestDto.getMemberPriorities() != null && !requestDto.getMemberPriorities().isEmpty()) {
                        for (FamilyMemberPriorityDto priorityDto : requestDto.getMemberPriorities()) {
                                User memberUser = userRepository.findById(priorityDto.getUserId())
                                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                                Supporter memberSupporter = supporterRepository.findByUserAndFamily(memberUser, family)
                                                .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));

                                // 우선순위 유효성 검사
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

                family.updateInviteCode(newInviteCode); // Assuming updateInviteCode method in Family
                familyRepository.save(family);

                return newInviteCode;
        }

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

                // 대표자가 자기 자신을 삭제하는 경우 (탈퇴 API 사용 유도)
                if (authenticatedUser.getId().equals(memberUserToDelete.getId())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "자기 자신을 삭제하려면 가족 탈퇴 API를 사용해주세요.");
                }

                Supporter supporterToDelete = supporterRepository.findByUserAndFamily(memberUserToDelete, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.SUPPORTER_NOT_FOUND));

                supporterRepository.delete(supporterToDelete);
        }

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
                                .relationship(null) // 가입하는 경우 관계는 기본적으로 null
                                .build();
                supporterRepository.save(newSupporter);

                return FamilySimpleResponseDto.of(family);
        }

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
