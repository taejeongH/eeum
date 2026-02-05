package org.ssafy.eeum.domain.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.global.common.model.BaseEntity;
import java.time.LocalDateTime;

@Entity
@Table(name = "voice_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoiceTask extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "job_id")
    private String jobId;

    @Column(name = "result_url", length = 500)
    private String resultUrl;

    @Builder.Default
    @Column(name = "poll_count")
    private Integer pollCount = 0;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    public void updateStatus(TaskStatus status) {
        this.status = status;
        this.lastPolledAt = LocalDateTime.now();
    }

    public void updateResult(String resultUrl) {
        this.status = TaskStatus.COMPLETED;
        this.resultUrl = resultUrl;
        this.lastPolledAt = LocalDateTime.now();
    }

    public void incrementPollCount() {
        this.pollCount = (this.pollCount == null) ? 1 : this.pollCount + 1;
        this.lastPolledAt = LocalDateTime.now();
    }

    public void fail(TaskStatus status) {
        this.status = status;
        this.lastPolledAt = LocalDateTime.now();
    }

    public enum TaskType {
        TRAINING, MESSAGE, SAMPLE
    }

    public enum TaskStatus {
        IN_QUEUE, IN_PROGRESS, COMPLETED, FAILED, ERROR, TIMEOUT
    }
}