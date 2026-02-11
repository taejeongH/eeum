package org.ssafy.eeum.domain.album.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.global.common.model.BaseEntity;

/**
 * 미디어 자산에 대한 변경 이력(로그)을 관리하는 엔티티입니다.
 * IoT 기기와의 동기화 상태를 추적하는 데 사용됩니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "media_logs")
public class MediaLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "media_id", nullable = false)
    private Integer mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    /**
     * MediaLog 객체를 생성합니다.
     * 
     * @summary 미디어 로그 생성
     * @param groupId    가족 식별자
     * @param mediaId    미디어 자산 식별자
     * @param actionType 수행된 작업 타입 (ADD, UPDATE, DELETE)
     */
    @Builder
    public MediaLog(Integer groupId, Integer mediaId, ActionType actionType) {
        this.groupId = groupId;
        this.mediaId = mediaId;
        this.actionType = actionType;
    }
}
