package org.ssafy.eeum.domain.message.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponseDto {

    private Integer id;
    private Integer groupId;
    private Integer senderUserId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private Boolean isRead;
    private LocalDateTime readAt;

    // 발신자 정보
    private String senderName;
    private String senderProfileImage;
    private String senderRelationship;
    private String senderRole;
    private Boolean enableTTS;
    private String voiceUrl;
}
