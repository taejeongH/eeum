package org.ssafy.eeum.domain.iot.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class IotSyncDto {
    private List<SyncItem> added;
    private List<Integer> deleted;
    private Integer lastLogId;
    private List<IotFamilyMemberDto> members;

    @Getter
    @Builder
    public static class SyncItem {
        private Integer id;
        private String url;
        private String description; // content for voice
        private String takenAt; // or createdAt
        private Integer userId;
        private String userName;
        private String profileImageUrl;
    }
}
