package org.ssafy.eeum.domain.schedule.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Schedule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false)
    private CategoryType categoryType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "visitor_name")
    private String visitorName;

    @Column(name = "visit_purpose")
    private String visitPurpose;

    @Column(name = "is_visited")
    private Boolean isVisited;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type")
    private RepeatType repeatType;

    @Column(name = "is_lunar")
    private Boolean isLunar;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "target_person")
    private String targetPerson;

    @Column(name = "recurrence_end_at")
    private LocalDateTime recurrenceEndAt;

    public void update(String title, LocalDateTime startAt, LocalDateTime endAt, LocalDateTime recurrenceEndAt,
            String description, String visitorName, String visitPurpose,
            RepeatType repeatType, Boolean isLunar, String targetPerson) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recurrenceEndAt = recurrenceEndAt;
        this.description = description;
        this.visitorName = visitorName;
        this.visitPurpose = visitPurpose;
        this.repeatType = repeatType;
        this.isLunar = isLunar;
        this.targetPerson = targetPerson;
    }

    public void updateBasicInfo(String title, LocalDateTime startAt, LocalDateTime endAt, String description,
            String targetPerson) {
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.description = description;
        this.targetPerson = targetPerson;
    }

    public void updateVisitInfo(String visitorName, String visitPurpose) {
        this.visitorName = visitorName;
        this.visitPurpose = visitPurpose;
    }

    public void updateVisitStatus(Boolean isVisited) {
        this.isVisited = isVisited;
    }
}