package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.domain.iot.dto.IotDeviceInfoResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotDevicePairRequestDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceRequestDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotPairingCodeResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotSimpleDeviceInfoResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceUpdateRequestDTO;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.global.auth.jwt.JwtProperties;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import org.ssafy.eeum.domain.iot.dto.IotDeviceInitResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotStreamingIpRequestDTO;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.ssafy.eeum.domain.iot.event.IotDeviceEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IoT 기기 등록, 관리, 페어링 및 인증 토큰 갱신 등을 담당하는 서비스 클래스입니다.
 * 기기의 상태 변경이나 삭제 시 관련 이벤트를 발행하여 실시간 처리를 지원합니다.
 * 
 * @summary IoT 기기 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IotDeviceService {

        private final IotDeviceRepository iotDeviceRepository;
        private final FamilyRepository familyRepository;
        private final SupporterRepository supporterRepository;
        private final RedisTemplate<String, Object> redisTemplate;
        private final JwtProvider jwtProvider;
        private final JwtProperties jwtProperties;
        private final ApplicationEventPublisher eventPublisher;

        private static final String PAIRING_PREFIX = "PAIR:";
        private static final String REFRESH_TOKEN_PREFIX = "RT:IOT:";
        private static final String OLD_REFRESH_TOKEN_PREFIX = "RT:IOT:OLD:";

        /**
         * 새로운 IoT 기기를 시스템에 등록합니다.
         * 중복된 시리얼 번호는 허용되지 않습니다.
         * 
         * @summary IoT 기기 등록
         * @param familyId 가족 그룹 식별자
         * @param request  기기 등록 정보 DTO
         * @return 생성된 기기 식별자
         */
        @Transactional
        public Integer registerDevice(Integer familyId, IotDeviceRequestDTO request) {
                if (iotDeviceRepository.existsBySerialNumber(request.getSerialNumber())) {
                        throw new CustomException(ErrorCode.DUPLICATE_RESOURCE);
                }

                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                IotDevice device = IotDevice.builder()
                                .family(family)
                                .serialNumber(request.getSerialNumber())
                                .deviceName(request.getDeviceName())
                                .locationType(request.getLocationType())
                                .build();

                iotDeviceRepository.save(device);
                return device.getId();
        }

        /**
         * 특정 가족 그룹에 속한 모든 IoT 기기 목록을 조회합니다.
         * 
         * @summary 가족별 IoT 기기 목록 조회
         * @param groupId 가족 그룹 식별자
         * @return 기기 정보 응답 DTO 리스트
         */
        public List<IotDeviceResponseDTO> getDevicesByGroup(Integer groupId) {
                return iotDeviceRepository.findAllByFamilyId(groupId).stream()
                                .map(IotDeviceResponseDTO::of)
                                .collect(Collectors.toList());
        }

        /**
         * 기기의 이름이나 설치 위치 정보를 업데이트합니다.
         * 위치 변경 시 실시간 동기화를 위해 업데이트 이벤트를 발행합니다.
         * 
         * @summary IoT 기기 정보 수정
         * @param userDetails 요청자 인증 정보
         * @param deviceId    기기 식별자
         * @param updateDto   수정할 정보 DTO
         */
        @Transactional
        public void updateDevice(CustomUserDetails userDetails, Integer deviceId,
                        IotDeviceUpdateRequestDTO updateDto) {
                IotDevice device = iotDeviceRepository.findById(deviceId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                // 해당 기기가 속한 그룹의 멤버인지 확인
                supporterRepository.findByUserAndFamily(userDetails.getUser(), device.getFamily())
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                String oldLocation = device.getLocationType();
                device.updateInfo(updateDto.getDeviceName(), updateDto.getLocationType());

                // 위치가 변경되었을 경우 기기에 알림 발행
                if (updateDto.getLocationType() != null && !updateDto.getLocationType().equals(oldLocation)) {
                        eventPublisher.publishEvent(
                                        IotDeviceEvent.updated(device.getSerialNumber(), device.getLocationType()));
                }
        }

        /**
         * IoT 기기를 삭제(등록 해제)합니다.
         * 삭제 전 기기에 상태 변경을 알리기 위해 삭제 이벤트를 발행합니다.
         * 
         * @summary IoT 기기 삭제
         * @param userDetails 요청자 인증 정보
         * @param deviceId    기기 식별자
         */
        @Transactional
        public void deleteDevice(CustomUserDetails userDetails, Integer deviceId) {
                IotDevice device = iotDeviceRepository.findById(deviceId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                // 해당 기기가 속한 그룹의 멤버인지 확인
                supporterRepository.findByUserAndFamily(userDetails.getUser(), device.getFamily())
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                // 삭제 전 기기에 알림 발행
                eventPublisher.publishEvent(IotDeviceEvent.deleted(device.getSerialNumber()));

                iotDeviceRepository.delete(device);
        }

        public List<IotSimpleDeviceInfoResponseDTO> getDevicesBySerialNumber(String serialNumber) {
                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));

                if (device.getFamily() == null) {
                        throw new CustomException(ErrorCode.IOT_DEVICE_GROUP_NOT_FOUND);
                }

                return iotDeviceRepository.findAllByFamilyId(device.getFamily().getId()).stream()
                                .map(IotSimpleDeviceInfoResponseDTO::of)
                                .collect(Collectors.toList());
        }

        /**
         * 기기 페어링을 위한 8자리 난수 코드를 생성합니다.
         * 생성된 코드는 Redis에 저장되며 3분간 유효합니다. QR 코드 생성을 위한 콘텐츠 정보도 함께 반환합니다.
         * 
         * @summary 페어링 코드 생성
         * @param groupId 가족 그룹 식별자
         * @return 페어링 코드 및 QR 콘텐츠 정보 DTO
         */
        @Transactional
        public IotPairingCodeResponseDTO generatePairingCode(Integer groupId) {

                if (!familyRepository.existsById(groupId)) {
                        throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
                }

                String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String key = PAIRING_PREFIX + code;
                long expiresIn = 180;

                redisTemplate.opsForValue().set(key, String.valueOf(groupId), Duration.ofSeconds(expiresIn));

                long ts = System.currentTimeMillis() / 1000L;
                String qrContent = String.format("{\"svc\":\"eeum\",\"code\":\"%s\",\"ts\":%d}", code, ts);

                return IotPairingCodeResponseDTO.builder()
                                .pairingCode(code)
                                .expiresIn(expiresIn)
                                .qrContent(qrContent)
                                .build();
        }

        /**
         * 페어링 코드를 검증하고 기기들을 가족 그룹에 연결(페어링)합니다.
         * 페어링 성공 시 IoT 기기 전용 액세스 토큰과 리프레시 토큰을 발급합니다.
         * 
         * @summary IoT 기기 페어링 처리
         * @param pairRequest 페어링 요청 정보
         * @return 초기화 정보(토큰 포함)
         */
        @Transactional
        public IotDeviceInitResponseDTO pairDevice(IotDevicePairRequestDTO pairRequest) {
                Integer familyId = getFamilyIdFromPairingCode(pairRequest.getPairingCode());
                Family family = getValidatedFamily(familyId);

                validateMasterDevice(pairRequest.getMasterSerialNumber(), pairRequest.getDevices());
                registerOrUpdateDevices(family, pairRequest.getDevices());

                String accessToken = jwtProvider.createDeviceAccessToken(familyId);
                String refreshToken = jwtProvider.createDeviceRefreshToken(familyId);

                storeRefreshToken(familyId, refreshToken);
                redisTemplate.delete(PAIRING_PREFIX + pairRequest.getPairingCode());

                return createInitResponse(pairRequest.getMasterSerialNumber(), familyId, accessToken, refreshToken);
        }

        private Integer getFamilyIdFromPairingCode(String pairingCode) {
                String key = PAIRING_PREFIX + pairingCode;
                Object pairedFamilyId = redisTemplate.opsForValue().get(key);
                if (pairedFamilyId != null) {
                        return Integer.valueOf(pairedFamilyId.toString());
                }
                throw new CustomException(ErrorCode.IOT_INVALID_PAIRING_CODE);
        }

        private Family getValidatedFamily(Integer familyId) {
                return familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
        }

        private void validateMasterDevice(String masterSerialNumber, List<IotDeviceInfoResponseDTO> devices) {
                boolean isMasterPresent = devices.stream()
                                .anyMatch(device -> device.getSerialNumber().equals(masterSerialNumber));

                if (isMasterPresent || iotDeviceRepository.existsBySerialNumber(masterSerialNumber)) {
                        return;
                }
                throw new CustomException(ErrorCode.IOT_MASTER_DEVICE_NOT_FOUND);
        }

        private void registerOrUpdateDevices(Family family, List<IotDeviceInfoResponseDTO> devices) {
                for (IotDeviceInfoResponseDTO deviceInfo : devices) {
                        IotDevice device = iotDeviceRepository.findBySerialNumber(deviceInfo.getSerialNumber())
                                        .orElseGet(() -> IotDevice.builder()
                                                        .serialNumber(deviceInfo.getSerialNumber())
                                                        .deviceType(deviceInfo.getDeviceType())
                                                        .family(family)
                                                        .build());

                        device.updatePairingInfo(family, deviceInfo.getDeviceType());
                        iotDeviceRepository.save(device);
                }
        }

        private void storeRefreshToken(Integer familyId, String refreshToken) {
                redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + familyId, refreshToken,
                                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
        }

        private IotDeviceInitResponseDTO createInitResponse(String serialNumber, Integer familyId, String accessToken,
                        String refreshToken) {
                return IotDeviceInitResponseDTO.builder()
                                .status("success")
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .serialNumber(serialNumber)
                                .groupId(familyId)
                                .build();
        }

        /**
         * IoT 기기의 인증 토큰을 갱신합니다.
         * 리프레시 토큰을 확인하여 새로운 액세스 토큰과 리프레시 토큰(회전 방식)을 발급합니다.
         * 
         * @summary IoT 기기 인증 토큰 갱신
         * @param serialNumber 기기 시리얼 번호
         * @param refreshToken 현재 리프레시 토큰
         * @return 갱신된 토큰 정보
         */
        @Transactional
        public IotDeviceInitResponseDTO refreshDeviceTokens(String serialNumber, String refreshToken) {
                validateRefreshTokenPresence(refreshToken);

                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));
                Integer familyId = device.getFamily().getId();

                log.debug("[기기 토큰 갱신] 요청 수신 - 시리얼: {}, 가족ID: {}", serialNumber, familyId);

                String currentTokenKey = REFRESH_TOKEN_PREFIX + familyId;
                String savedCurrentToken = (String) redisTemplate.opsForValue().get(currentTokenKey);

                if (isTokenRotationRequired(refreshToken, savedCurrentToken)) {
                        return handleTokenRotation(familyId, serialNumber, savedCurrentToken);
                }

                return handleTokenRotationGracePeriod(familyId, serialNumber, refreshToken, savedCurrentToken);
        }

        private void validateRefreshTokenPresence(String refreshToken) {
                if (refreshToken != null) {
                        return;
                }
                log.warn("[기기 토큰 갱신] 리프레시 토큰이 누락되었습니다.");
                throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        private boolean isTokenRotationRequired(String requestedToken, String savedCurrentToken) {
                return savedCurrentToken != null && savedCurrentToken.equals(requestedToken);
        }

        private IotDeviceInitResponseDTO handleTokenRotation(Integer familyId, String serialNumber,
                        String oldRefreshToken) {
                log.info("[기기 토큰 갱신] 토큰 회전 실행 - 가족ID: {}", familyId);

                validateJwt(oldRefreshToken);

                String newAccessToken = jwtProvider.createDeviceAccessToken(familyId);
                String newRefreshToken = jwtProvider.createDeviceRefreshToken(familyId);

                updateTokenStorage(familyId, oldRefreshToken, newRefreshToken);

                return createInitResponse(serialNumber, familyId, newAccessToken, newRefreshToken);
        }

        private void validateJwt(String token) {
                if (jwtProvider.validateToken(token)) {
                        return;
                }
                log.warn("[기기 토큰 갱신] JWT 유효성 검증 실패");
                throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        private void updateTokenStorage(Integer familyId, String oldRefreshToken, String newRefreshToken) {
                String currentKey = REFRESH_TOKEN_PREFIX + familyId;
                String oldKey = OLD_REFRESH_TOKEN_PREFIX + familyId;

                redisTemplate.opsForValue().set(oldKey, oldRefreshToken, Duration.ofMinutes(3));
                redisTemplate.opsForValue().set(currentKey, newRefreshToken,
                                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
        }

        private IotDeviceInitResponseDTO handleTokenRotationGracePeriod(Integer familyId, String serialNumber,
                        String requestedToken, String savedCurrentToken) {
                String oldKey = OLD_REFRESH_TOKEN_PREFIX + familyId;
                String savedOldToken = (String) redisTemplate.opsForValue().get(oldKey);

                if (savedOldToken != null && savedOldToken.equals(requestedToken)) {
                        validateJwt(requestedToken);
                        String newAccessToken = jwtProvider.createDeviceAccessToken(familyId);
                        return createInitResponse(serialNumber, familyId, newAccessToken, savedCurrentToken);
                }

                log.warn("[기기 토큰 갱신] 유효하지 않은 리프레시 토큰 요청 - 가족ID: {}", familyId);
                throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        /**
         * 가족 그룹의 스트리밍 URL을 업데이트합니다.
         * 
         * @summary 스트리밍 URL 업데이트
         * @param familyId     가족 그룹 식별자
         * @param streamingUrl 새로운 스트리밍 URL
         */
        @Transactional
        public void updateStreamingUrl(Integer familyId, String streamingUrl) {
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
                family.updateStreamingUrl(streamingUrl);
        }

        /**
         * 특정 IP 주소를 기반으로 스트리밍 URL을 생성하여 업데이트합니다.
         * 주로 IoT 기기에서 전송된 로컬 IP를 바탕으로 스트리밍 경로를 설정할 때 사용됩니다.
         * 
         * @summary 기기 IP 기반 스트리밍 URL 업데이트
         * @param familyId   가족 그룹 식별자
         * @param requestDto IP 정보를 포함한 요청 DTO
         */
        @Transactional
        public void updateStreamingIp(Integer familyId, IotStreamingIpRequestDTO requestDto) {
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                String streamingUrl = String.format("http://%s:8000/api/iot/device/falls/stream",
                                requestDto.getIpAddress());

                family.updateStreamingUrl(streamingUrl);
                familyRepository.save(family);
        }
}
