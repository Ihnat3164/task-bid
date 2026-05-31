package org.example.taskbid.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "task_reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = "task_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Profile customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "executor_id", nullable = false)
    private Profile executor;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
