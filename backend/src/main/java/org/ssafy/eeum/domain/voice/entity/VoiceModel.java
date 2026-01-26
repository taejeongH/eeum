package org.ssafy.eeum.domain.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.global.common.model.BaseEntity;

@Entity
@Table(name = "voice_models")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoiceModel extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "model_path", nullable = false)
    private String modelPath;

    @Enumerated(EnumType.STRING)
    private ModelStatus status;

    public enum ModelStatus {
        TRAINING, COMPLETED, ERROR
    }
}