package org.example.taskbid.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.dto.TrainingDatasetExportResponse;
import org.example.taskbid.service.RecommendationTrainingDatasetExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations/training")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RecommendationTrainingDatasetController {

    RecommendationTrainingDatasetExportService recommendationTrainingDatasetExportService;

    @PostMapping("/export")
    public ResponseEntity<TrainingDatasetExportResponse> export(
            @RequestParam(defaultValue = "data/real_training_dataset.csv") String outputPath
    ) {
        return ResponseEntity.ok(recommendationTrainingDatasetExportService.exportCsv(outputPath));
    }
}
