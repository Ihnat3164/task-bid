package org.example.taskbid.dto;

import lombok.Data;

@Data
public class TaskReviewRequest {
    private Integer rating;
    private String comment;
}
