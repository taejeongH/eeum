package org.ssafy.eeum.domain.iot.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/**
 * IoT 기기 간의 증분 동기화 데이터를 담는 DTO 클래스입니다.
 * 추가된 항목, 삭제된 항목 및 최신 로그 ID 정보를 포함합니다.
 * 
 * @summary IoT 증분 동기화 DTO
 */
@Getter
@Builder
public class IotSyncDto {
    private List<SyncItem> added;
    private List<Integer> deleted;
    private Integer lastLogId;
    private List<IotFamilyMemberDto> members;

    /**
     * 동기화 대상이 되는 개별 항목 정보입니다.
     */
    @Getter
    @Builder
    public static class SyncItem {
        private Integer id;
        private String url;
        private String description;
        private String takenAt;
        private Integer userId;
    }
}
