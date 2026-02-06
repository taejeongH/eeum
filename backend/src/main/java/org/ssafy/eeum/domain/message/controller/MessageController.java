package org.ssafy.eeum.domain.message.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.message.dto.MessageRequestDto;
import org.ssafy.eeum.domain.message.dto.MessageResponseDto;
import org.ssafy.eeum.domain.message.service.MessageService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/{groupId}/messages")
    public RestApiResponse<MessageResponseDto> send(
            @PathVariable Integer groupId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MessageRequestDto requestDto) {

        MessageResponseDto response = messageService.send(groupId, userDetails.getId(), requestDto);
        return RestApiResponse.success(response);
    }

    @GetMapping("/{groupId}/messages")
    public RestApiResponse<List<MessageResponseDto>> getMessages(
            @PathVariable Integer groupId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails userDetails) {

        List<MessageResponseDto> responses = messageService.getMessages(groupId, userDetails.getId());
        return RestApiResponse.success(responses);
    }

    @PatchMapping("/{groupId}/messages/{messageId}/read")
    public RestApiResponse<MessageResponseDto> markRead(
            @PathVariable Integer groupId,
            @PathVariable Integer messageId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails userDetails) {

        MessageResponseDto response = messageService.markRead(groupId, messageId, userDetails.getId());
        return RestApiResponse.success(response);
    }

    @DeleteMapping("/{groupId}/messages/{messageId}")
    public RestApiResponse<String> delete(
            @PathVariable Integer groupId,
            @PathVariable Integer messageId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails userDetails) {

        messageService.delete(groupId, messageId, userDetails.getId());
        return RestApiResponse.success("메시지가 삭제되었습니다.");
    }
}
