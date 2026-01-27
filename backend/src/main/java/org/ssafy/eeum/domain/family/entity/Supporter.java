package org.ssafy.eeum.domain.family.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ssafy.eeum.domain.auth.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "supporters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@IdClass(SupporterId.class)
public class Supporter {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Family family;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Integer emergencyPriority;

    private boolean representativeFlag;

    private String relationship;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime deletedAt;

    public enum Role {
        PATIENT, CAREGIVER
    }

    @Builder
    public Supporter(User user, Family family, Role role, boolean representativeFlag, String relationship) {
        this.user = user;
        this.family = family;
        this.role = role;
        this.representativeFlag = representativeFlag;
        this.relationship = relationship;
    }

    public void updateEmergencyPriority(Integer emergencyPriority) {
        this.emergencyPriority = emergencyPriority;
    }

    public void updateRelationship(String relationship) {
        this.relationship = relationship;
    }
}

