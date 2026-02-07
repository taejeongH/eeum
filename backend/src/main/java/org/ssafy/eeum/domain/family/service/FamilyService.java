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
        private final IotDeviceRepository iotDeviceRepository;
        private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        private static final int CODE_LENGTH = 8;
        private static final SecureRandom RANDOM = new SecureRandom();

        // ... existing methods ...

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

                // [Changed] Get JETSON device serial as streamingUrl
                // If multiple, pick first. If none, use family.getStreamingUrl() (fallback) or
                // null
                String streamingTarget = family.getStreamingUrl();
                List<org.ssafy.eeum.domain.iot.entity.IotDevice> devices = iotDeviceRepository
                                .findAllByFamilyId(familyId);
                for (org.ssafy.eeum.domain.iot.entity.IotDevice device : devices) {
                        if ("JETSON".equalsIgnoreCase(device.getDeviceType())) {
                                streamingTarget = device.getSerialNumber();
                                break;
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
