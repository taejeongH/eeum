package org.ssafy.eeum.domain.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays; // Added import
import java.util.List;
import java.util.StringJoiner; // Added import

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Supporter> supporters = new ArrayList<>();

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('M', 'F')")
    private Gender gender;

    @Column(length = 255)
    private String address;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "chronic_diseases", length = 255)
    private String chronicDiseases;

    @Column(length = 255)
    private String notes;

    @Column(name = "blood_type", length = 255)
    private String bloodType;

    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private boolean isEmailVerified = false;

    public enum Gender {
        M, F
    }

    // 카카오 로그인 정보 업데이트
    public void updateFromKakao(String name, String email, String profileImage) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
        if (email != null && !email.isEmpty()) {
            this.email = email;
        }
        if ((this.profileImage == null || this.profileImage.isEmpty()) && profileImage != null && !profileImage.isEmpty()) {
            this.profileImage = profileImage;
        }
    }

    public void updateProfile(String name, String phone, LocalDate birthDate, Gender gender, String address,
            String profileImage) {
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.gender = gender;
        this.address = address;
        this.profileImage = profileImage;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateHealthInfo(String bloodType, List<String> chronicDiseases) { // Changed parameter type
        if (bloodType != null) {
            this.bloodType = bloodType;
        }
        if (chronicDiseases != null && !chronicDiseases.isEmpty()) {
            StringJoiner sj = new StringJoiner(",");
            for (String disease : chronicDiseases) {
                sj.add(disease.trim());
            }
            this.chronicDiseases = sj.toString();
        } else if (chronicDiseases != null && chronicDiseases.isEmpty()) {
            this.chronicDiseases = null; // Clear if an empty list is passed
        }
        this.updatedAt = LocalDateTime.now();
    }

    public List<String> getChronicDiseasesList() {
        if (this.chronicDiseases == null || this.chronicDiseases.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(this.chronicDiseases.split(","));
    }
}