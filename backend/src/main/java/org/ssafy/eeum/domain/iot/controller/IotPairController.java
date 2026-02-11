package org.ssafy.eeum.domain.iot.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.iot.dto.IotPairingCodeResponseDTO;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

/**
 * 모바일 앱과 IoT 기기 간의 페어링을 위한 API를 제공하는 컨트롤러 클래스입니다.
 * QR 코드 생성을 위한 단기 인증 코드(Pairing Code) 발급 기능을 담당합니다.
 * 
 * @summary IoT 기기 페어링 컨트롤러
 */
@RestController
@RequestMapping("/api/families/{familyId}/iot/pair")
@RequiredArgsConstructor
@Tag(name = "IoT Pairing", description = "IoT 기기 페어링 관련 API")
public class IotPairController {

        private final IotDeviceService iotDeviceService;

        /**
         * 보관된 가족 그룹 정보를 바탕으로 IoT 기기 등록 시 사용할 단기 페어링 코드를 생성합니다.
         * 
         * @summary 페어링 코드 발급
         * @param userDetails 현재 사용자 정보
         * @param familyId    가족 그룹 식별자
         * @return 발급된 페어링 코드 응답 DTO
         */
        @SwaggerApiSpec(summary = "페어링 코드 발급", description = "QR 코드 생성을 위한 단기 페어링 코드를 발급받습니다.", successMessage = "페어링 코드 발급 성공", errors = {
                        ErrorCode.FAMILY_NOT_FOUND })
        @PostMapping("/code")
        public RestApiResponse<IotPairingCodeResponseDTO> generatePairingCode(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer familyId) {

                IotPairingCodeResponseDTO response = iotDeviceService.generatePairingCode(familyId);

                return RestApiResponse.success(HttpStatus.OK, "페어링 코드 발급 성공", response);
        }
}
