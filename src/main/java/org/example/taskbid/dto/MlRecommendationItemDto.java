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
public class MlRecommendationItemDto {
    private Long taskId;
    private Double score;
    private List<String> reasons;
}
