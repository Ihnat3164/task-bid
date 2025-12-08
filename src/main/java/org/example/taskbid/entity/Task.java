package org.example.taskbid.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    // необходимые навыки
    @ManyToMany
    @JoinTable(
            name = "task_skills",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> requiredSkills;
}
