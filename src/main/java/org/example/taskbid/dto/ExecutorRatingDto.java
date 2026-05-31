package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutorRatingDto {
    private Double averageRating;
    private Long reviewsCount;
}
