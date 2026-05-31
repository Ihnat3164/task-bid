package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RealTrainingRowDto {
    private String executorId;
    private Long taskId;
    private String executorCity;
    private String taskCity;
    private Integer viewLabel;
    private Integer matchedSkillsCount;
    private Double skillsOverlapRatio;
    private Double cityMatch;
    private Double categoryAffinity;
    private Double freshnessBonus;
    private Integer requiredSkillCount;
    private Integer executorSkillCount;
    private Double exactSkillCover;
    private Integer skillGapCount;
    private Double taskAgeHours;
    private Double baselineScore;
    private Double activityLevel;
    private Double qualityScore;
    private Integer applyLabel;
    private Integer approveLabel;
}
