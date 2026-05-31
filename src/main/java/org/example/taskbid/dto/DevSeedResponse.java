package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DevSeedResponse {
    private boolean created;
    private String message;
    private String customerEmail;
    private String executorOneEmail;
    private String executorTwoEmail;
    private String password;
    private int usersCreated;
    private int tasksCreated;
    private int applicationsCreated;
    private int totalDemoUsers;
    private int totalDemoTasks;
}
