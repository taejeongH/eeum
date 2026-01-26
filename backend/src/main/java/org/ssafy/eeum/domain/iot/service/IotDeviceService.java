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

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IotDeviceService {

    private final IotDeviceRepository iotDeviceRepository;

    @Transactional
    public Integer registerDevice(IotDeviceRequestDTO request) {
        if (iotDeviceRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE);
        }

        IotDevice device = IotDevice.builder()
                .groupId(request.getGroupId())
                .serialNumber(request.getSerialNumber())
                .deviceName(request.getDeviceName())
                .locationType(request.getLocationType())
                .build();

        iotDeviceRepository.save(device);
        return device.getId();
    }

    public List<IotDeviceResponseDTO> getDevicesByGroup(Integer groupId) {
        return iotDeviceRepository.findAllByGroupId(groupId).stream()
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

        return iotDeviceRepository.findAllByGroupId(device.getGroupId()).stream()
                .map(IotDeviceMqttDTO::of)
                .collect(Collectors.toList());
    }
}
