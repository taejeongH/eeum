package org.ssafy.eeum.domain.message.service;

import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final SupporterRepository supporterRepository;

    @Transactional
    public MessageResponseDto send(Integer groupId, Integer senderUserId, MessageRequestDto requestDto) {
        Family group = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        supporterRepository.findByUserAndFamily(sender, group)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

        Message message = Message.builder()
                .group(group)
                .sender(sender)
                .content(requestDto.getContent())
                .isRead(false)
                .build();

        Message saved = messageRepository.save(message);
        return toDto(saved);
    }

    public List<MessageResponseDto> getMessages(Integer groupId, Integer requesterUserId) {
        Family group = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        supporterRepository.findByUserAndFamily(requester, group)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

        return messageRepository.findAllByGroupAndDeletedAtIsNullOrderByCreatedAtAsc(group)
                .stream()
                .map(this::toDto)
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
        return toDto(message);
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
    }

    private MessageResponseDto toDto(Message message) {
        // 발신자의 Supporter 정보를 찾기
        Supporter senderSupporter = supporterRepository.findByUserAndFamily(message.getSender(), message.getGroup())
                .orElse(null);
        
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
                .enableTTS(true) // TODO: 실제 TTS 설정값으로 변경
                .build();
    }
}
