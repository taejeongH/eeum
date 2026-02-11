package org.ssafy.eeum.domain.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ssafy.eeum.domain.family.entity.Supporter;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * 시스템 사용자 정보를 관리하는 엔티티 클래스입니다.
 * 사용자의 기본 인적 사항, 보안 정보, 건강 정보 및 대표 목소리 샘플 등을 관리합니다.
 * 
 * @summary 사용자 엔티티
 */
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

    @JsonIgnore
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

    @Column(name = "fcm_token", length = 1000)
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_sample_id")
    private VoiceSample representativeSample;

    public enum Gender {
        M, F
    }

    /**
     * 카카오 로그인 정보를 기반으로 사용자 정보를 업데이트합니다.
     * 
     * @summary 카카오 프로필 기반 업데이트
     * @param name         이름
     * @param email        이메일
     * @param profileImage 프로필 이미지 URL
     */
    public void updateFromKakao(String name, String email, String profileImage) {
        if ((this.name == null || this.name.isEmpty()) && name != null && !name.isEmpty()) {
            this.name = name;
        }
        if ((this.email == null || this.email.isEmpty()) && email != null && !email.isEmpty()) {
            this.email = email;
        }
        if ((this.profileImage == null || this.profileImage.isEmpty()) && profileImage != null
                && !profileImage.isEmpty()) {
            this.profileImage = profileImage;
        }
    }

    /**
     * 사용자의 프로필 이미지를 변경합니다.
     * 
     * @summary 프로필 이미지 업데이트
     * @param profileImage 새로운 프로필 이미지 URL
     */
    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자의 기본 프로필 정보를 통합 업데이트합니다.
     * 
     * @summary 사용자 프로필 수정
     * @param name         이름
     * @param phone        전화번호
     * @param birthDate    생년월일
     * @param gender       성별
     * @param address      주소
     * @param profileImage 프로필 이미지 URL
     */
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

    /**
     * 사용자의 건강 정보(혈액형, 지병)를 업데이트합니다.
     * 지병 목록은 콤마(,)로 구분된 문자열로 변환되어 저장됩니다.
     * 
     * @summary 건강 정보 업데이트
     * @param bloodType       혈액형
     * @param chronicDiseases 지병 리스트
     */
    public void updateHealthInfo(String bloodType, List<String> chronicDiseases) {
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
            this.chronicDiseases = null;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 푸시 알림을 위한 FCM 토큰을 업데이트합니다.
     * 
     * @summary FCM 토큰 업데이트
     * @param fcmToken 새로운 FCM 토큰
     */
    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 콤마로 연결된 지병 문자열을 리스트 형태로 조회합니다.
     * 
     * @summary 지병 목록 리스트 조회
     * @return 지병 리스트
     */
    public List<String> getChronicDiseasesList() {
        if (this.chronicDiseases == null || this.chronicDiseases.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(this.chronicDiseases.split(","));
    }

    /**
     * 사용자의 비밀번호를 업데이트합니다.
     * 
     * @summary 비밀번호 수정
     * @param password 새 비밀번호 (암호화된 상태)
     */
    public void updatePassword(String password) {
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자의 대표 목소리 샘플을 설정합니다.
     * 
     * @summary 대표 목소리 샘플 설정
     * @param sample 목소리 샘플 엔티티
     */
    public void updateRepresentativeSample(VoiceSample sample) {
        this.representativeSample = sample;
        this.updatedAt = LocalDateTime.now();
    }
}
