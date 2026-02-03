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

        public List<IotDeviceResponseDTO> getDevicesByGroup(Integer groupId) {
                return iotDeviceRepository.findAllByFamilyId(groupId).stream()
                                .map(IotDeviceResponseDTO::of)
                                .collect(Collectors.toList());
        }

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

        @Transactional
        public IotPairingCodeResponseDTO generatePairingCode(Integer groupId) {
                // 1. 해당 그룹 존재 확인
                if (!familyRepository.existsById(groupId)) {
                        throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
                }

                // 2. 짧은 희귀 코드 생성 (8자리)
                String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String key = PAIRING_PREFIX + code;
                long expiresIn = 180; // 3분

                // 3. Redis 저장 (3분 만료)
                redisTemplate.opsForValue().set(key, String.valueOf(groupId), Duration.ofSeconds(expiresIn));

                // 4. QR 콘텐츠 생성 (JSON)
                long ts = System.currentTimeMillis() / 1000L;
                String qrContent = String.format("{\"svc\":\"eeum\",\"code\":\"%s\",\"ts\":%d}", code, ts);

                return IotPairingCodeResponseDTO.builder()
                                .pairingCode(code)
                                .expiresIn(expiresIn)
                                .qrContent(qrContent)
                                .build();
        }

        @Transactional
        public IotDeviceInitResponseDTO pairDevice(IotDevicePairRequestDTO request) {
                String pairingCode = request.getPairingCode();
                String masterSerialNumber = request.getMasterSerialNumber();

                // 1. 페어링 코드 검증
                String key = PAIRING_PREFIX + pairingCode;
                Object val = redisTemplate.opsForValue().get(key);
                if (val == null) {
                        throw new CustomException(ErrorCode.IOT_INVALID_PAIRING_CODE);
                }
                Integer familyId = Integer.valueOf(val.toString());

                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                // 2. 마스터 기기 존재 및 신규 여부 체크 (등록 리스트에 포함되어야 함)
                boolean isMasterInList = request.getDevices().stream()
                                .anyMatch(d -> d.getSerialNumber().equals(masterSerialNumber));

                if (!isMasterInList && !iotDeviceRepository.existsBySerialNumber(masterSerialNumber)) {
                        throw new CustomException(ErrorCode.IOT_MASTER_DEVICE_NOT_FOUND);
                }

                // 3. 기기 목록 처리 (Find or Create)
                for (IotDeviceInfoResponseDTO info : request.getDevices()) {
                        IotDevice device = iotDeviceRepository.findBySerialNumber(info.getSerialNumber())
                                        .orElseGet(() -> IotDevice.builder()
                                                        .serialNumber(info.getSerialNumber())
                                                        .deviceType(info.getDeviceType())
                                                        .family(family)
                                                        .build());

                        device.updatePairingInfo(family, info.getDeviceType());
                        iotDeviceRepository.save(device);
                }

                // 3. 해당 그룹의 가족 통합 토큰 생성 (가족 공용)
                String familyAt = jwtProvider.createDeviceAccessToken(familyId);
                String familyRt = jwtProvider.createDeviceRefreshToken(familyId);

                // 가족 단위로 리프레시 토큰 저장
                redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + familyId, familyRt,
                                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

                // 페어링 성공 시 코드 삭제
                redisTemplate.delete(key);

                // 4. 응답 생성 (마스터 기기용 토큰 포함)
                return IotDeviceInitResponseDTO.builder()
                                .status("success")
                                .accessToken(familyAt)
                                .refreshToken(familyRt)
                                .serialNumber(masterSerialNumber)
                                .groupId(familyId)
                                .build();
        }

        @Transactional
        public IotDeviceInitResponseDTO refreshDeviceTokens(String serialNumber, String refreshToken) {
                // 1. 기기 존재 확인
                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));

                Integer familyId = device.getFamily().getId();

                // 2. Redis에서 해당 가족의 Refresh Token 조회
                String currentKey = REFRESH_TOKEN_PREFIX + familyId;
                String oldKey = OLD_REFRESH_TOKEN_PREFIX + familyId;

                String savedCurrentToken = (String) redisTemplate.opsForValue().get(currentKey);

                // 로그: 현재 저장된 토큰 정보 출력 (디버깅용)
                String reqTokenPart = (refreshToken != null)
                                ? refreshToken.substring(0, Math.min(20, refreshToken.length()))
                                : "NULL";
                String savedTokenPart = (savedCurrentToken != null)
                                ? savedCurrentToken.substring(0, Math.min(20, savedCurrentToken.length()))
                                : "NULL";

                log.debug("[IOT-REFRESH] Refresh request. Serial: {}, FamilyId: {}, ReqToken: {}..., SavedToken: {}...",
                                serialNumber, familyId, reqTokenPart, savedTokenPart);

                // 토큰 null 체크
                if (refreshToken == null) {
                        log.warn("[IOT-REFRESH] Refresh token is NULL. Serial: {}, FamilyId: {}", serialNumber,
                                        familyId);
                        throw new CustomException(ErrorCode.INVALID_TOKEN);
                }

                String finalAccessToken;
                String finalRefreshToken;

                // 상황 1: 현재 유효한 토큰(Current)으로 요청한 경우 -> 진짜 갱신(Rotation) 진행
                if (savedCurrentToken != null && savedCurrentToken.equals(refreshToken)) {
                        log.info("[IOT-REFRESH] Real Rotation triggered. Serial: {}, FamilyId: {}", serialNumber,
                                        familyId);

                        // 3. 토큰 유효성 자체 검증 (만료 여부 등)
                        if (!jwtProvider.validateToken(refreshToken)) {
                                log.warn("[IOT-REFRESH] Current JWT validation failed. Serial: {}, FamilyId: {}",
                                                serialNumber, familyId);
                                throw new CustomException(ErrorCode.INVALID_TOKEN);
                        }

                        // 4. 새로운 가족 통합 토큰 쌍 생성
                        finalAccessToken = jwtProvider.createDeviceAccessToken(familyId);
                        finalRefreshToken = jwtProvider.createDeviceRefreshToken(familyId);

                        // 5. Redis 업데이트
                        // 현재 토큰을 '이전 토큰'으로 이동 (3분 유예)
                        redisTemplate.opsForValue().set(oldKey, savedCurrentToken, Duration.ofMinutes(3));
                        // 새 토큰을 '현재 토큰'으로 저장
                        redisTemplate.opsForValue().set(currentKey, finalRefreshToken,
                                        Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));

                        log.info("[IOT-REFRESH] Rotation success. New Current RT saved. Serial: {}, FamilyId: {}",
                                        serialNumber, familyId);
                }
                // 상황 2: 이미 갱신된 '이전 토큰(Old)'으로 요청한 경우 -> 따라잡기(Catch-up) 진행
                else {
                        String savedOldToken = (String) redisTemplate.opsForValue().get(oldKey);

                        if (savedOldToken != null && savedOldToken.equals(refreshToken)) {
                                log.info("[IOT-REFRESH] Catch-up triggered. Serial: {}, FamilyId: {}. Returning existing Current RT.",
                                                serialNumber, familyId);

                                // 토큰 유효성 검증
                                if (!jwtProvider.validateToken(refreshToken)) {
                                        log.warn("[IOT-REFRESH] Old JWT validation failed. Serial: {}, FamilyId: {}",
                                                        serialNumber, familyId);
                                        throw new CustomException(ErrorCode.INVALID_TOKEN);
                                }

                                // 새 Access Token은 발급해주되, Refresh Token은 Redis에 저장되어 있는 '현재 거'를 그대로 줌
                                finalAccessToken = jwtProvider.createDeviceAccessToken(familyId);
                                finalRefreshToken = savedCurrentToken; // 따라잡기 핵심

                                if (finalRefreshToken == null) {
                                        // 혹시라도 그 사이에 현재 토큰마저 사라졌다면 에러
                                        log.error("[IOT-REFRESH] Critical: Catch-up failed because Current RT is missing in Redis. FamilyId: {}",
                                                        familyId);
                                        throw new CustomException(ErrorCode.INVALID_TOKEN);
                                }
                        } else {
                                // 둘 다 안 맞으면 진짜 유효하지 않은 토큰
                                log.warn("[IOT-REFRESH] Token mismatch. Serial: {}, FamilyId: {}", serialNumber,
                                                familyId);
                                throw new CustomException(ErrorCode.INVALID_TOKEN);
                        }
                }

                return IotDeviceInitResponseDTO.builder()
                                .status("success")
                                .accessToken(finalAccessToken)
                                .refreshToken(finalRefreshToken)
                                .serialNumber(serialNumber)
                                .groupId(familyId)
                                .build();
        }

        @Transactional
        public void updateStreamingUrl(Integer familyId, String streamingUrl) {
                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
                family.updateStreamingUrl(streamingUrl);
        }

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
