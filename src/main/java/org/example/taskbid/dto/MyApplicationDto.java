package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;
import org.example.taskbid.entity.enums.ApplicationStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class MyApplicationDto {
    private Long applicationId;
    private Long taskId;
    private String taskTitle;
    private String taskCity;
    private ApplicationStatus status;
    private LocalDateTime createdAt;

    public MyApplicationDto(
            Long applicationId,
            Long taskId,
            String taskTitle,
            String taskCity,
            ApplicationStatus status,
            LocalDateTime createdAt
    ) {
        this.applicationId = applicationId;
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.taskCity = taskCity;
        this.status = status;
        this.createdAt = createdAt;
    }
}
