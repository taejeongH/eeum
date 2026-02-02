package org.ssafy.eeum.domain.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.global.common.model.BaseEntity;

@Entity
@Table(name = "voice_samples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoiceSample extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = true)
    private VoiceScript voiceScript;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "sample_path", nullable = false, length = 255)
    private String samplePath;

    @Column(name = "duration_sec", nullable = false)
    private Double durationSec;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "test_audio_path")
    private String testAudioPath;

    public void updateSamplePath(String newPath) {
        this.samplePath = newPath;
    }

    public void updateTestAudioPath(String path) {
        this.testAudioPath = path;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}