package org.ssafy.eeum.domain.voice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "voice_scripts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoiceScript {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "script_order", nullable = false)
    private Integer scriptOrder;
}