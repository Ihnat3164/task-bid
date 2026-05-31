package org.example.taskbid.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendation_impressions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationImpression {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executor_id", nullable = false)
    private UUID executorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executor_id", insertable = false, updatable = false)
    private Profile executor;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private Task task;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private LocalDateTime recommendedAt;

    private String mode;

    private String modelVersion;

    private Double score;

    private String scoreType;

    @Column(columnDefinition = "TEXT")
    private String reasonsJson;

    private String requestId;
}
