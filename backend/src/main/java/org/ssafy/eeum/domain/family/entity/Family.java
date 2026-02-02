package org.ssafy.eeum.domain.family.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.auth.entity.User;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "family_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Family {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Supporter> supporters = new ArrayList<>();

    @Column(nullable = false)
    private String groupName;

    @Column(unique = true)
    private String inviteCode;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "last_media_log_id", nullable = false)
    private Integer lastMediaLogId = 0;

    @Column(name = "last_voice_log_id", nullable = false)
    private Integer lastVoiceLogId = 0;

    @Builder
    public Family(String groupName, String inviteCode, User user) {
        this.groupName = groupName;
        this.inviteCode = inviteCode;
        this.user = user;
    }

    public void updateGroupName(String newGroupName) {
        this.groupName = newGroupName;
    }

    public void updateInviteCode(String newInviteCode) {
        this.inviteCode = newInviteCode;
    }

    public void updateLastMediaLogId(Integer logId) {
        this.lastMediaLogId = logId;
    }

    public void updateLastVoiceLogId(Integer logId) {
        this.lastVoiceLogId = logId;
    }
}
