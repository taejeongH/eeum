package org.ssafy.eeum.domain.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.global.common.model.BaseEntity;

@Entity
@Table(name = "voice_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoiceLog extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "voice_id", nullable = false)
    private Integer voiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;
}
