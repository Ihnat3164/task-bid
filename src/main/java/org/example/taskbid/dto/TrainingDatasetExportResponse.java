package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrainingDatasetExportResponse {
    private String path;
    private Integer rows;
    private Integer uniqueExecutorTaskPairs;
    private Integer positiveApplyPairs;
    private Integer duplicatePairs;
    private Double positiveRateByPairs;
    private Integer sourceImpressionRows;
    private Integer sourceDuplicateImpressionPairs;
}
