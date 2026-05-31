package org.example.taskbid.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.taskbid.entity.enums.TaskViewSource;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_view_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskViewEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", insertable = false, updatable = false)
    private Profile viewer;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private Task task;

    @Column(nullable = false)
    private LocalDateTime viewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskViewSource source;

    private String requestId;
}
