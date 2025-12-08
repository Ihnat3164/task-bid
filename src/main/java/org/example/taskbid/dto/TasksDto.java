package org.example.taskbid.dto;

import lombok.Data;

@Data
public class TasksDto {
    private Long id;
    private String title;
    private String status;
    private String beginDate;
}
