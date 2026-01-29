package org.ssafy.eeum.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class MessageRequestDto {

    @NotBlank
    private String content;
}
