package org.example.taskbid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationResponse {
    private List<Long> recommendedTaskIds;
    private List<MlRecommendationItemDto> recommendations;
    private String mode;
    private String modelVersion;
    private String scoreType;
}
