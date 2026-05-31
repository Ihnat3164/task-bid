package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TaskReviewResponse {
    private Long id;
    private Long taskId;
    private UUID executorId;
    private UUID customerId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
