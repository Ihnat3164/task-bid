package org.example.taskbid.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.taskbid.entity.enums.ApplicationStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "executor_profile_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TaskApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(optional = false)
    @JoinColumn(name = "executor_profile_id", nullable = false)
    private Profile executor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    private String price;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

