package org.ssafy.eeum.domain.family.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.user.entity.User;
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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

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
}
