package org.ssafy.eeum.domain.message.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.domain.message.dto.MessageRequestDto;
import org.ssafy.eeum.domain.message.dto.MessageResponseDto;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

        private final org.ssafy.eeum.domain.message.repository.MessageRepository messageRepository;
        private final org.ssafy.eeum.domain.family.repository.FamilyRepository familyRepository;
        private final org.ssafy.eeum.domain.auth.repository.UserRepository userRepository;
        private final org.ssafy.eeum.domain.family.repository.SupporterRepository supporterRepository;
        private final org.ssafy.eeum.domain.voice.service.VoiceService voiceService;
        private final org.ssafy.eeum.domain.iot.service.IotSyncService iotSyncService; // Handled
        private final org.ssafy.eeum.global.infra.s3.S3Service s3Service;
        private final org.ssafy.eeum.domain.voice.repository.VoiceLogRepository voiceLogRepository;

        @Transactional
        public MessageResponseDto send(Integer groupId, Integer senderUserId, MessageRequestDto requestDto) {
                // 1. 기본 검증 로직
                Family group = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                User sender = userRepository.findById(senderUserId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                supporterRepository.findByUserAndFamily(sender, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                // 2. 메시지 객체 생성 (일단 DB에 저장하여 ID를 확보)
                Message message = Message.builder()
                                .group(group)
                                .sender(sender)
                                .content(requestDto.getContent())
                                .isRead(false)
                                .isSynced(false)
                                .build();

                // 3. 메시지 먼저 저장 (TTS 실패 시에도 롤백되지 않도록 함)
                Message saved = messageRepository.save(message);

                // 4. TTS 생성 및 업데이트 (예외가 발생해도 현재 트랜잭션에 영향을 주지 않도록 내부에서 완전 격리)
                try {
                        // voiceService.createTtsUrl 내부에서 예외가 발생해도 catch 문으로 이동함
                        String voiceUrl = voiceService.createTtsUrl(senderUserId, requestDto.getContent());
                        if (voiceUrl != null) {
                                saved.updateVoiceUrl(voiceUrl);
                                // JPA 더티 체킹에 의해 메서드 종료 시 voiceUrl이 업데이트됨
                        }
                } catch (Exception e) {
                        // "학습된 음성 샘플이 없습니다" 등의 에러를 여기서 차단
                        log.warn("TTS 생성이 중단되었습니다 (메시지 전송은 계속됨): {}", e.getMessage());
                }

                // 5. IoT 동기화 알림 (목소리가 없더라도 텍스트 업데이트 알림은 보냄)
                try {
                        // Log 저장 (ADD)
                        saveLog(groupId, saved.getId(), org.ssafy.eeum.domain.iot.entity.ActionType.ADD);
                        iotSyncService.notifyUpdate(groupId, "voice", 1);
                } catch (Exception e) {
                        log.error("IoT Sync Notification failed: {}", e.getMessage());
                }

                // Auth check already retrieved the supporter (sender's supporter)
                Supporter senderSupporter = supporterRepository.findByUserAndFamily(sender, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                return toDto(saved, senderSupporter);
        }

        public List<MessageResponseDto> getMessages(Integer groupId, Integer requesterUserId) {
                Family group = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                User requester = userRepository.findById(requesterUserId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                supporterRepository.findByUserAndFamily(requester, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                List<Message> messages = messageRepository.findAllByGroupAndDeletedAtIsNullOrderByCreatedAtAsc(group);
                List<Supporter> supporters = supporterRepository.findAllByFamily(group);

                // Map: UserId -> Supporter
                java.util.Map<Integer, Supporter> supporterMap = supporters.stream()
                                .collect(java.util.stream.Collectors.toMap(
                                                s -> s.getUser().getId(),
                                                s -> s,
                                                (existing, replacement) -> existing));

                return messages.stream()
                                .map(msg -> toDto(msg, supporterMap.get(msg.getSender().getId())))
                                .toList();
        }

        @Transactional
        public MessageResponseDto markRead(Integer groupId, Integer messageId, Integer requesterUserId) {
                Family group = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                User requester = userRepository.findById(requesterUserId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                supporterRepository.findByUserAndFamily(requester, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                if (!message.getGroup().getId().equals(group.getId())) {
                        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
                }

                message.markRead();

                Supporter senderSupporter = supporterRepository
                                .findByUserAndFamily(message.getSender(), group)
                                .orElse(null);

                return toDto(message, senderSupporter);
        }

        @Transactional
        public void delete(Integer groupId, Integer messageId, Integer requesterUserId) {
                Family group = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                User requester = userRepository.findById(requesterUserId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                supporterRepository.findByUserAndFamily(requester, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                if (!message.getGroup().getId().equals(group.getId())) {
                        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
                }

                if (!message.getSender().getId().equals(requester.getId())) {
                        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
                }

                message.softDelete();

                // Log 저장 (DELETE)
                saveLog(groupId, messageId, org.ssafy.eeum.domain.iot.entity.ActionType.DELETE);

                // IoT 동기화 알림
                iotSyncService.notifyUpdate(groupId, "voice", 1);
        }

        @Transactional
        public org.ssafy.eeum.domain.album.dto.AlbumDTOs.IotAlbumSyncResponseDTO syncForIot(Integer familyId) {
                List<Message> unsyncedMessages = messageRepository.findAllByGroupIdAndIsSyncedFalse(familyId);

                List<org.ssafy.eeum.domain.album.dto.AlbumDTOs.AlbumSyncItemResponseDTO> addedItems = new java.util.ArrayList<>();
                List<Integer> deletedIds = new java.util.ArrayList<>();
                List<Integer> syncedIds = new java.util.ArrayList<>();

                for (Message msg : unsyncedMessages) {
                        if (msg.getDeletedAt() != null) {
                                deletedIds.add(msg.getId());
                        } else if (msg.getVoiceUrl() != null) {
                                addedItems.add(org.ssafy.eeum.domain.album.dto.AlbumDTOs.AlbumSyncItemResponseDTO
                                                .builder()
                                                .id(msg.getId())
                                                .url(s3Service.getPresignedUrl(msg.getVoiceUrl()))
                                                .description(msg.getContent())
                                                .takenAt(msg.getCreatedAt().toLocalDate())
                                                .build());
                        }
                        syncedIds.add(msg.getId());
                }

                if (!syncedIds.isEmpty()) {
                        messageRepository.markAsSynced(syncedIds);
                }

                return org.ssafy.eeum.domain.album.dto.AlbumDTOs.IotAlbumSyncResponseDTO.builder()
                                .added(addedItems)
                                .deleted(deletedIds)
                                .build();
        }

        private MessageResponseDto toDto(Message message, Supporter senderSupporter) {
                String senderRelationship = null;
                String senderRole = null;
                if (senderSupporter != null) {
                        senderRelationship = senderSupporter.getRelationship();
                        senderRole = senderSupporter.getRole().name();
                }

                return MessageResponseDto.builder()
                                .id(message.getId())
                                .groupId(message.getGroup().getId())
                                .senderUserId(message.getSender().getId())
                                .content(message.getContent())
                                .createdAt(message.getCreatedAt())
                                .deletedAt(message.getDeletedAt())
                                .isRead(message.getIsRead())
                                .readAt(message.getReadAt())
                                .senderName(message.getSender().getName())
                                .senderProfileImage(message.getSender().getProfileImage())
                                .senderRelationship(senderRelationship)
                                .senderRole(senderRole)
                                .enableTTS(message.getVoiceUrl() != null) // 실제 URL 유무에 따라 클라이언트에 TTS 가능 여부 전달
                                .voiceUrl(message.getVoiceUrl() != null
                                                ? s3Service.getPresignedUrl(message.getVoiceUrl())
                                                : null)
                                .build();
        }

        private void saveLog(Integer familyId, Integer voiceId,
                        org.ssafy.eeum.domain.iot.entity.ActionType actionType) {
                org.ssafy.eeum.domain.voice.entity.VoiceLog log = org.ssafy.eeum.domain.voice.entity.VoiceLog.builder()
                                .groupId(familyId)
                                .voiceId(voiceId)
                                .actionType(actionType)
                                .build();
                voiceLogRepository.save(log);
        }
}