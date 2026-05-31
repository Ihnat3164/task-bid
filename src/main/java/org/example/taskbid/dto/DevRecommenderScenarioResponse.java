package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DevRecommenderScenarioResponse {
    private boolean seedCreated;
    private Integer usersCreated;
    private Integer tasksCreated;
    private Integer totalDemoUsers;
    private Integer totalDemoTasks;
    private java.util.List<String> behaviorPatterns;
    private Integer sessionsPerExecutor;
    private Integer recommendationRequests;
    private Integer recommendationImpressionsCreated;
    private Integer taskViewsCreated;
    private Integer appliesCreated;
    private Integer appliesPersisted;
    private Integer approvesDone;
    private Integer tasksDone;
    private Integer completesDone;
    private String exportedCsvPath;
    private Integer exportedRows;
    private Integer positiveApplyLabels;
}
