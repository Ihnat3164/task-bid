package org.example.taskbid.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.dto.MlRecommendationItemDto;
import org.example.taskbid.dto.MlRecommendationResponse;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.RecommendationImpression;
import org.example.taskbid.entity.Task;
import org.example.taskbid.repositiry.RecommendationImpressionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RecommendationImpressionService {

    RecommendationImpressionRepository recommendationImpressionRepository;
    ObjectMapper objectMapper;

    @Transactional
    public void logImpressions(
            Profile executor,
            List<Task> returnedTasks,
            MlRecommendationResponse recommendationResponse
    ) {
        if (executor == null || returnedTasks == null || returnedTasks.isEmpty()) {
            return;
        }

        Map<Long, MlRecommendationItemDto> recommendationByTaskId = Optional
                .ofNullable(recommendationResponse)
                .map(MlRecommendationResponse::getRecommendations)
                .orElse(List.of())
                .stream()
                .filter(item -> item.getTaskId() != null)
                .collect(Collectors.toMap(
                        MlRecommendationItemDto::getTaskId,
                        Function.identity(),
                        (left, right) -> left
                ));

        String mode = recommendationResponse == null ? null : recommendationResponse.getMode();
        String modelVersion = recommendationResponse == null ? null : recommendationResponse.getModelVersion();
        String scoreType = recommendationResponse == null ? null : recommendationResponse.getScoreType();
        LocalDateTime recommendedAt = LocalDateTime.now();

        List<RecommendationImpression> impressions = new ArrayList<>();
        for (int index = 0; index < returnedTasks.size(); index++) {
            Task task = returnedTasks.get(index);
            MlRecommendationItemDto recommendationItem = recommendationByTaskId.get(task.getId());

            impressions.add(RecommendationImpression.builder()
                    .executorId(executor.getId())
                    .taskId(task.getId())
                    .position(index + 1)
                    .recommendedAt(recommendedAt)
                    .mode(mode)
                    .modelVersion(modelVersion)
                    .score(recommendationItem == null ? null : recommendationItem.getScore())
                    .scoreType(scoreType)
                    .reasonsJson(toReasonsJson(recommendationItem))
                    .requestId(null)
                    .build());
        }

        recommendationImpressionRepository.saveAll(impressions);
    }

    private String toReasonsJson(MlRecommendationItemDto recommendationItem) {
        if (recommendationItem == null || recommendationItem.getReasons() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(recommendationItem.getReasons());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
