package org.ssafy.eeum.global.auth.dto;

import lombok.*;

public class AuthDTOs {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenRefreshRequestDTO {
        private String refreshToken;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenResponseDTO {
        private String accessToken;
        private String refreshToken; 
    }
}
