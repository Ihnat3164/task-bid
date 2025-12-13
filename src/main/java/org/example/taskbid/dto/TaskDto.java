package org.example.taskbid.dto;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskDto {
    private Long id;
    private String title;
    private String description;
    private String city;
    private TaskStatus status;
    private List<Skill> requiredSkills;
    private LocalDateTime createdAt;
}

