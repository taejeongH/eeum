package org.ssafy.eeum.domain.message.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.album.dto.AlbumDTOs;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.message.dto.MessageRequestDto;
import org.ssafy.eeum.domain.message.dto.MessageResponseDto;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

        private final MessageRepository messageRepository;
        private final FamilyRepository familyRepository;
        private final UserRepository userRepository;
        private final SupporterRepository supporterRepository;
        private final IotSyncService iotSyncService; // Handled
        private final S3Service s3Service;
        private final VoiceLogRepository voiceLogRepository;
        private final MessageTtsAsyncService messageTtsAsyncService;

        @Transactional
        public MessageResponseDto send(Integer groupId, Integer senderUserId, MessageRequestDto requestDto) {
                // 기본 검증 로직
                Family group = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                User sender = userRepository.findById(senderUserId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                supporterRepository.findByUserAndFamily(sender, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                // 메시지 객체 생성
                Message message = Message.builder()
                                .group(group)
                                .sender(sender)
                                .content(requestDto.getContent())
                                .isRead(false)
                                .isSynced(false)
                                .build();

                // 메시지 먼저 저장
                Message saved = messageRepository.save(message);

                // 비동기로 TTS 생성 및 후속 처리 요청
                messageTtsAsyncService.processTtsAsync(saved.getId(), senderUserId, requestDto.getContent(), groupId);

                Supporter senderSupporter = supporterRepository.findByUserAndFamily(sender, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                return toDto(saved, senderSupporter);
        }

        @Transactional
        public void generateTts(Integer userId, Integer groupId, String text) {
                Family group = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
                User sender = userRepository.findById(userId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                Message message = Message.builder()
                                .group(group)
                                .sender(sender)
                                .content(text)
                                .isRead(false)
                                .isSynced(false)
                                .build();
                Message saved = messageRepository.save(message);

                messageTtsAsyncService.processTtsAsync(saved.getId(), userId, text, groupId);
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

                Supporter requesterSupporter = supporterRepository.findByUserAndFamily(requester, group)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                Message message = messageRepository.findByIdAndDeletedAtIsNull(messageId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                if (!message.getGroup().getId().equals(group.getId())) {
                        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
                }

                boolean isSender = message.getSender().getId().equals(requester.getId());
                boolean isRepresentative = requesterSupporter.isRepresentativeFlag();

                if (!isSender && !isRepresentative) {
                        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
                }

                message.softDelete();
                saveLog(groupId, messageId, ActionType.DELETE);
                iotSyncService.notifyUpdate(groupId, "voice");
        }

        @Transactional
        public AlbumDTOs.IotAlbumSyncResponseDTO syncForIot(Integer familyId) {
                List<Message> unsyncedMessages = messageRepository.findAllByGroupIdAndIsSyncedFalse(familyId);

                List<AlbumDTOs.AlbumSyncItemResponseDTO> addedItems = new java.util.ArrayList<>();
                List<Integer> deletedIds = new java.util.ArrayList<>();
                List<Integer> syncedIds = new java.util.ArrayList<>();

                for (Message msg : unsyncedMessages) {
                        if (msg.getDeletedAt() != null) {
                                deletedIds.add(msg.getId());
                        } else if (msg.getVoiceUrl() != null) {
                                addedItems.add(AlbumDTOs.AlbumSyncItemResponseDTO
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

                return AlbumDTOs.IotAlbumSyncResponseDTO.builder()
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
                                .enableTTS(message.getVoiceUrl() != null)
                                .voiceUrl(message.getVoiceUrl() != null
                                                ? s3Service.getPresignedUrl(message.getVoiceUrl())
                                                : null)
                                .build();
        }

        private void saveLog(Integer familyId, Integer voiceId, ActionType actionType) {
                VoiceLog log = VoiceLog.builder()
                                .groupId(familyId)
                                .voiceId(voiceId)
                                .actionType(actionType)
                                .build();
                voiceLogRepository.save(log);
        }
}