package org.ssafy.eeum.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Family family;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String type;

    private Integer relatedId;

    @Builder
    public Notification(Family family, String title, String message, String type, Integer relatedId) {
        this.family = family;
        this.title = title;
        this.message = message;
        this.type = type;
        this.relatedId = relatedId;
    }
}
