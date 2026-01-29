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

@RestController
@RequestMapping("/api/families/{familyId}/iot/pair")
@RequiredArgsConstructor
@Tag(name = "IoT Pairing", description = "IoT 기기 페어링 관련 API")
public class IotPairController {

        private final IotDeviceService iotDeviceService;

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
