package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.iot.dto.IotDeviceMqttDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceRequestDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceResponseDTO;
import org.ssafy.eeum.domain.iot.dto.IotDeviceUpdateDTO;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IotDeviceService {

    private final IotDeviceRepository iotDeviceRepository;
    private final FamilyRepository familyRepository;

    @Transactional
    public Integer registerDevice(IotDeviceRequestDTO request) {
        if (iotDeviceRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Family family = familyRepository.findById(request.getGroupId())
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

    public List<IotDeviceResponseDTO> getDevicesByGroup(Long groupId) {
        return iotDeviceRepository.findAllByFamilyId(groupId).stream()
                .map(IotDeviceResponseDTO::of)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateDevice(Integer deviceId, IotDeviceUpdateDTO updateDto) {
        IotDevice device = iotDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        device.updateInfo(updateDto.getDeviceName(), updateDto.getLocationType());
    }

    @Transactional
    public void deleteDevice(Integer deviceId) {
        IotDevice device = iotDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        iotDeviceRepository.delete(device);
    }

    public List<IotDeviceMqttDTO> getDevicesBySerialNumber(String serialNumber) {
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        return iotDeviceRepository.findAllByFamilyId(device.getFamily().getId()).stream()
                .map(IotDeviceMqttDTO::of)
                .collect(Collectors.toList());
    }
}
