package org.example.taskbid.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateTaskRequest {
    private String title;
    private String description;
    private String city;
    // список id навыков из таблицы skills (опционально)
    private List<Long> skillIds;
}

