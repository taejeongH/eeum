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

    @Column(name = "gpt_path")
    private String gptPath;

    @Column(name = "sovits_path")
    private String sovitsPath;

    @Enumerated(EnumType.STRING)
    private ModelStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_sample_id")
    private VoiceSample representativeSample;

    public void updateModel(String gptPath, String sovitsPath) {
        this.gptPath = gptPath;
        this.sovitsPath = sovitsPath;
        this.status = ModelStatus.COMPLETED;
    }

    public void updateStatus(ModelStatus status) {
        this.status = status;
    }

    public void updateRepresentativeSample(VoiceSample sample) {
        this.representativeSample = sample;
    }

    public enum ModelStatus {
        TRAINING, COMPLETED, ERROR
    }
}