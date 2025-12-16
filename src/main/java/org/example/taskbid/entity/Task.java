package org.example.taskbid.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.taskbid.entity.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"author", "requiredSkills"})
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    private LocalDateTime createdAt;

    // заказчик (создатель) — профиль
    @ManyToOne
    @JoinColumn(name = "author_profile_id", nullable = false)
    private Profile author;

    @ManyToOne
    @JoinColumn(name = "executor_profile_id")
    private Profile executor;

    @ManyToMany
    @JoinTable(
            name = "task_skills",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> requiredSkills;
}
