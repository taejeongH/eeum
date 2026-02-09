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

                
                supporterRepository.findByUserAndFamily(userDetails.getUser(), device.getFamily())
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                String oldLocation = device.getLocationType();
                device.updateInfo(updateDto.getDeviceName(), updateDto.getLocationType());

                
                if (updateDto.getLocationType() != null && !updateDto.getLocationType().equals(oldLocation)) {
                        eventPublisher.publishEvent(
                                        IotDeviceEvent.updated(device.getSerialNumber(), device.getLocationType()));
                }
        }

        @Transactional
        public void deleteDevice(CustomUserDetails userDetails, Integer deviceId) {
                IotDevice device = iotDeviceRepository.findById(deviceId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                
                supporterRepository.findByUserAndFamily(userDetails.getUser(), device.getFamily())
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                
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

        @Transactional
        public IotDeviceInitResponseDTO pairDevice(IotDevicePairRequestDTO request) {
                String pairingCode = request.getPairingCode();
                String masterSerialNumber = request.getMasterSerialNumber();

                String key = PAIRING_PREFIX + pairingCode;
                Object val = redisTemplate.opsForValue().get(key);
                if (val == null) {
                        throw new CustomException(ErrorCode.IOT_INVALID_PAIRING_CODE);
                }
                Integer familyId = Integer.valueOf(val.toString());

                Family family = familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                boolean isMasterInList = request.getDevices().stream()
                                .anyMatch(d -> d.getSerialNumber().equals(masterSerialNumber));

                if (!isMasterInList && !iotDeviceRepository.existsBySerialNumber(masterSerialNumber)) {
                        throw new CustomException(ErrorCode.IOT_MASTER_DEVICE_NOT_FOUND);
                }

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

                String familyAt = jwtProvider.createDeviceAccessToken(familyId);
                String familyRt = jwtProvider.createDeviceRefreshToken(familyId);

                redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + familyId, familyRt,
                                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
                redisTemplate.delete(key);

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

                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));

                Integer familyId = device.getFamily().getId();

                String currentKey = REFRESH_TOKEN_PREFIX + familyId;
                String oldKey = OLD_REFRESH_TOKEN_PREFIX + familyId;

                String savedCurrentToken = (String) redisTemplate.opsForValue().get(currentKey);

                String reqTokenPart = (refreshToken != null)
                                ? refreshToken.substring(0, Math.min(20, refreshToken.length()))
                                : "NULL";
                String savedTokenPart = (savedCurrentToken != null)
                                ? savedCurrentToken.substring(0, Math.min(20, savedCurrentToken.length()))
                                : "NULL";

                log.debug("[IOT-REFRESH] Refresh request. Serial: {}, FamilyId: {}, ReqToken: {}..., SavedToken: {}...",
                                serialNumber, familyId, reqTokenPart, savedTokenPart);

                if (refreshToken == null) {
                        log.warn("[IOT-REFRESH] Refresh token is NULL. Serial: {}, FamilyId: {}", serialNumber,
                                        familyId);
                        throw new CustomException(ErrorCode.INVALID_TOKEN);
                }

                String finalAccessToken;
                String finalRefreshToken;

                if (savedCurrentToken != null && savedCurrentToken.equals(refreshToken)) {
                        log.info("[IOT-REFRESH] Real Rotation triggered. Serial: {}, FamilyId: {}", serialNumber,
                                        familyId);

                        if (!jwtProvider.validateToken(refreshToken)) {
                                log.warn("[IOT-REFRESH] Current JWT validation failed. Serial: {}, FamilyId: {}",
                                                serialNumber, familyId);
                                throw new CustomException(ErrorCode.INVALID_TOKEN);
                        }

                        finalAccessToken = jwtProvider.createDeviceAccessToken(familyId);
                        finalRefreshToken = jwtProvider.createDeviceRefreshToken(familyId);

                        redisTemplate.opsForValue().set(oldKey, savedCurrentToken, Duration.ofMinutes(3));
                        redisTemplate.opsForValue().set(currentKey, finalRefreshToken,
                                        Duration.ofMillis(jwtProperties.getRefreshTokenExpiration()));
                }
                else {
                        String savedOldToken = (String) redisTemplate.opsForValue().get(oldKey);

                        if (savedOldToken != null && savedOldToken.equals(refreshToken)) {
                                if (!jwtProvider.validateToken(refreshToken)) {
                                        throw new CustomException(ErrorCode.INVALID_TOKEN);
                                }

                                finalAccessToken = jwtProvider.createDeviceAccessToken(familyId);
                                finalRefreshToken = savedCurrentToken;

                                if (finalRefreshToken == null) {
                                        throw new CustomException(ErrorCode.INVALID_TOKEN);
                                }
                        } else {
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
