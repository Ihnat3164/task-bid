package org.example.taskbid.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.dto.RealTrainingRowDto;
import org.example.taskbid.dto.TrainingDatasetExportResponse;
import org.example.taskbid.entity.*;
import org.example.taskbid.entity.enums.ApplicationStatus;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.RecommendationImpressionRepository;
import org.example.taskbid.repositiry.TaskApplicationRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.TaskViewEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RecommendationTrainingDatasetExportService {

    static int ACTIVITY_WINDOW_DAYS = 30;
    static int ACTIVITY_NORMALIZER = 10;
    static double MATCHED_SKILL_WEIGHT = 3.0;
    static double OVERLAP_RATIO_WEIGHT = 2.0;
    static double CITY_MATCH_WEIGHT = 1.5;
    static double CATEGORY_AFFINITY_WEIGHT = 1.0;
    static double FRESHNESS_WEIGHT = 1.0;

    RecommendationImpressionRepository recommendationImpressionRepository;
    TaskApplicationRepository taskApplicationRepository;
    TaskViewEventRepository taskViewEventRepository;
    ProfileRepository profileRepository;
    TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<RealTrainingRowDto> buildRows() {
        List<RecommendationImpression> impressions = recommendationImpressionRepository.findAllByOrderByRecommendedAtAscIdAsc();
        List<TaskApplication> applications = taskApplicationRepository.findAll();
        List<TaskViewEvent> viewEvents = taskViewEventRepository.findAll();
        Map<UUID, Profile> profilesById = profileRepository.findAll().stream()
                .collect(Collectors.toMap(Profile::getId, profile -> profile));
        Map<Long, Task> tasksById = taskRepository.findAll().stream()
                .collect(Collectors.toMap(Task::getId, task -> task));
        Map<PairKey, List<RecommendationImpression>> impressionsByPair = impressions.stream()
                .collect(Collectors.groupingBy(impression -> new PairKey(
                        impression.getExecutorId(),
                        impression.getTaskId()
                )));

        return impressionsByPair.values().stream()
                .map(pairImpressions -> toTrainingRowForPair(
                        pairImpressions,
                        applications,
                        viewEvents,
                        profilesById,
                        tasksById
                ))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RealTrainingRowDto::getExecutorId)
                        .thenComparing(RealTrainingRowDto::getTaskId))
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainingDatasetExportResponse exportCsv(String outputPath) {
        List<RealTrainingRowDto> rows = buildRows();
        Path path = Path.of(outputPath);

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, toCsv(rows));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to export recommendation training dataset", ex);
        }

        DatasetDiagnostics diagnostics = diagnostics(rows);
        SourceDiagnostics sourceDiagnostics = sourceDiagnostics();

        return TrainingDatasetExportResponse.builder()
                .path(path.toString())
                .rows(rows.size())
                .uniqueExecutorTaskPairs(diagnostics.uniqueExecutorTaskPairs())
                .positiveApplyPairs(diagnostics.positiveApplyPairs())
                .duplicatePairs(diagnostics.duplicatePairs())
                .positiveRateByPairs(diagnostics.positiveRateByPairs())
                .sourceImpressionRows(sourceDiagnostics.sourceImpressionRows())
                .sourceDuplicateImpressionPairs(sourceDiagnostics.sourceDuplicateImpressionPairs())
                .build();
    }

    private RealTrainingRowDto toTrainingRowForPair(
            List<RecommendationImpression> pairImpressions,
            List<TaskApplication> applications,
            List<TaskViewEvent> viewEvents,
            Map<UUID, Profile> profilesById,
            Map<Long, Task> tasksById
    ) {
        if (pairImpressions == null || pairImpressions.isEmpty()) {
            return null;
        }

        PairKey pairKey = new PairKey(pairImpressions.get(0).getExecutorId(), pairImpressions.get(0).getTaskId());
        Profile executor = profilesById.get(pairKey.executorId());
        Task task = tasksById.get(pairKey.taskId());
        if (executor == null || task == null) {
            return null;
        }

        Optional<TaskApplication> firstApplication = applications.stream()
                .filter(application -> Objects.equals(application.getExecutor().getId(), pairKey.executorId()))
                .filter(application -> Objects.equals(application.getTask().getId(), pairKey.taskId()))
                .min(Comparator.comparing(TaskApplication::getCreatedAt));

        Optional<RecommendationImpression> attributedPositiveImpression = firstApplication
                .flatMap(application -> latestImpressionBeforeApply(pairImpressions, application));

        RecommendationImpression selectedImpression = attributedPositiveImpression
                .orElseGet(() -> latestImpression(pairImpressions));
        TaskApplication attributedApplication = attributedPositiveImpression.isPresent()
                ? firstApplication.orElse(null)
                : null;

        return toTrainingRow(selectedImpression, executor, task, attributedApplication, applications, viewEvents);
    }

    private Optional<RecommendationImpression> latestImpressionBeforeApply(
            List<RecommendationImpression> impressions,
            TaskApplication application
    ) {
        return impressions.stream()
                .filter(impression -> impression.getRecommendedAt() != null)
                .filter(impression -> !impression.getRecommendedAt().isAfter(application.getCreatedAt()))
                .max(Comparator.comparing(RecommendationImpression::getRecommendedAt)
                        .thenComparing(RecommendationImpression::getId));
    }

    private RecommendationImpression latestImpression(List<RecommendationImpression> impressions) {
        return impressions.stream()
                .max(Comparator.comparing(RecommendationImpression::getRecommendedAt)
                        .thenComparing(RecommendationImpression::getId))
                .orElseThrow(() -> new IllegalArgumentException("pair impressions must not be empty"));
    }

    private RealTrainingRowDto toTrainingRow(
            RecommendationImpression impression,
            Profile executor,
            Task task,
            TaskApplication attributedApplication,
            List<TaskApplication> applications,
            List<TaskViewEvent> viewEvents
    ) {
        if (executor == null || task == null) {
            return null;
        }

        LocalDateTime recommendedAt = impression.getRecommendedAt();

        int matchedSkills = matchedSkillsCount(executor, task);
        double overlapRatio = skillsOverlapRatio(matchedSkills, task);
        double cityMatch = cityMatch(executor.getCity(), task.getCity());
        double categoryAffinity = categoryAffinity(executor, task);
        double freshnessBonus = freshnessBonus(task.getCreatedAt(), recommendedAt);
        int requiredSkillCount = requiredSkillCount(task);
        int executorSkillCount = executorSkillCount(executor);
        double exactSkillCover = exactSkillCover(matchedSkills, overlapRatio);
        int skillGapCount = skillGapCount(requiredSkillCount, matchedSkills);
        double taskAgeHours = taskAgeHours(task.getCreatedAt(), recommendedAt);
        double baselineScore = matchedSkills == 0
                ? 0.0
                : matchedSkills * MATCHED_SKILL_WEIGHT
                + overlapRatio * OVERLAP_RATIO_WEIGHT
                + cityMatch * CITY_MATCH_WEIGHT
                + categoryAffinity * CATEGORY_AFFINITY_WEIGHT
                + freshnessBonus * FRESHNESS_WEIGHT;

        int applyLabel = attributedApplication == null ? 0 : 1;
        int approveLabel = attributedApplication != null && attributedApplication.getStatus().isApproved() ? 1 : 0;
        int viewLabel = hasViewAfterImpression(viewEvents, executor, task, recommendedAt, attributedApplication) ? 1 : 0;

        return RealTrainingRowDto.builder()
                .executorId(executor.getId().toString())
                .taskId(task.getId())
                .executorCity(nullToEmpty(executor.getCity()))
                .taskCity(nullToEmpty(task.getCity()))
                .viewLabel(viewLabel)
                .matchedSkillsCount(matchedSkills)
                .skillsOverlapRatio(round4(overlapRatio))
                .cityMatch(round4(cityMatch))
                .categoryAffinity(round4(categoryAffinity))
                .freshnessBonus(round4(freshnessBonus))
                .requiredSkillCount(requiredSkillCount)
                .executorSkillCount(executorSkillCount)
                .exactSkillCover(round4(exactSkillCover))
                .skillGapCount(skillGapCount)
                .taskAgeHours(round4(taskAgeHours))
                .baselineScore(round4(baselineScore))
                .activityLevel(round4(activityLevel(applications, executor, recommendedAt)))
                .qualityScore(round4(qualityScore(applications, executor, recommendedAt)))
                .applyLabel(applyLabel)
                .approveLabel(approveLabel)
                .build();
    }

    private int matchedSkillsCount(Profile executor, Task task) {
        Set<Long> executorSkillIds = skillIds(executor.getSkills());
        Set<Long> taskSkillIds = skillIds(task.getRequiredSkills());
        executorSkillIds.retainAll(taskSkillIds);
        return executorSkillIds.size();
    }

    private int requiredSkillCount(Task task) {
        return skillIds(task.getRequiredSkills()).size();
    }

    private int executorSkillCount(Profile executor) {
        return skillIds(executor.getSkills()).size();
    }

    private double skillsOverlapRatio(int matchedSkills, Task task) {
        Set<Long> taskSkillIds = skillIds(task.getRequiredSkills());
        if (taskSkillIds.isEmpty()) {
            return 0.0;
        }
        return (double) matchedSkills / taskSkillIds.size();
    }

    private double cityMatch(String executorCity, String taskCity) {
        if (executorCity == null || taskCity == null) {
            return 0.0;
        }
        return executorCity.trim().equalsIgnoreCase(taskCity.trim()) ? 1.0 : 0.0;
    }

    private double categoryAffinity(Profile executor, Task task) {
        Set<String> executorCategories = categories(executor.getSkills());
        Set<String> taskCategories = categories(task.getRequiredSkills());
        if (taskCategories.isEmpty()) {
            return 0.0;
        }
        executorCategories.retainAll(taskCategories);
        return (double) executorCategories.size() / taskCategories.size();
    }

    private double exactSkillCover(int matchedSkills, double overlapRatio) {
        return matchedSkills > 0 && overlapRatio >= 0.9999 ? 1.0 : 0.0;
    }

    private int skillGapCount(int requiredSkillCount, int matchedSkills) {
        return Math.max(0, requiredSkillCount - matchedSkills);
    }

    private double taskAgeHours(LocalDateTime taskCreatedAt, LocalDateTime recommendedAt) {
        if (taskCreatedAt == null || recommendedAt == null) {
            return 999.0;
        }

        double ageHours = Math.max(0.0, java.time.Duration.between(taskCreatedAt, recommendedAt).toSeconds() / 3600.0);
        return Math.min(ageHours, 999.0);
    }

    private double freshnessBonus(LocalDateTime taskCreatedAt, LocalDateTime recommendedAt) {
        if (taskCreatedAt == null || recommendedAt == null) {
            return 0.0;
        }

        long ageHours = java.time.Duration.between(taskCreatedAt, recommendedAt).toHours();
        if (ageHours < 0 || ageHours <= 24) {
            return 1.0;
        }
        if (ageHours <= 72) {
            return 0.5;
        }
        return 0.0;
    }

    private double activityLevel(List<TaskApplication> applications, Profile executor, LocalDateTime recommendedAt) {
        LocalDateTime windowStart = recommendedAt.minusDays(ACTIVITY_WINDOW_DAYS);
        long appliesInWindow = applications.stream()
                .filter(application -> Objects.equals(application.getExecutor().getId(), executor.getId()))
                .filter(application -> application.getCreatedAt().isBefore(recommendedAt))
                .filter(application -> !application.getCreatedAt().isBefore(windowStart))
                .count();
        return Math.min(1.0, (double) appliesInWindow / ACTIVITY_NORMALIZER);
    }

    private double qualityScore(List<TaskApplication> applications, Profile executor, LocalDateTime recommendedAt) {
        List<TaskApplication> priorApplications = applications.stream()
                .filter(application -> Objects.equals(application.getExecutor().getId(), executor.getId()))
                .filter(application -> application.getCreatedAt().isBefore(recommendedAt))
                .toList();

        if (priorApplications.isEmpty()) {
            return 0.5;
        }

        long accepted = priorApplications.stream()
                .filter(application -> application.getStatus().isApproved())
                .count();
        return (double) accepted / priorApplications.size();
    }

    private boolean hasViewAfterImpression(
            List<TaskViewEvent> viewEvents,
            Profile executor,
            Task task,
            LocalDateTime recommendedAt,
            TaskApplication attributedApplication
    ) {
        return viewEvents.stream()
                .anyMatch(event -> Objects.equals(event.getViewerId(), executor.getId())
                        && Objects.equals(event.getTaskId(), task.getId())
                        && !event.getViewedAt().isBefore(recommendedAt)
                        && (attributedApplication == null || !event.getViewedAt().isAfter(attributedApplication.getCreatedAt())));
    }

    private DatasetDiagnostics diagnostics(List<RealTrainingRowDto> rows) {
        Map<PairKey, Long> rowsByPair = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> new PairKey(UUID.fromString(row.getExecutorId()), row.getTaskId()),
                        Collectors.counting()
                ));
        long positiveApplyPairs = rows.stream()
                .filter(row -> row.getApplyLabel() != null && row.getApplyLabel() == 1)
                .map(row -> new PairKey(UUID.fromString(row.getExecutorId()), row.getTaskId()))
                .distinct()
                .count();
        long duplicatePairs = rowsByPair.values().stream()
                .filter(count -> count > 1)
                .count();
        int uniquePairs = rowsByPair.size();
        return new DatasetDiagnostics(
                uniquePairs,
                (int) positiveApplyPairs,
                (int) duplicatePairs,
                uniquePairs == 0 ? 0.0 : round4((double) positiveApplyPairs / uniquePairs)
        );
    }

    private SourceDiagnostics sourceDiagnostics() {
        List<RecommendationImpression> impressions = recommendationImpressionRepository.findAll();
        Map<PairKey, Long> impressionsByPair = impressions.stream()
                .collect(Collectors.groupingBy(
                        impression -> new PairKey(impression.getExecutorId(), impression.getTaskId()),
                        Collectors.counting()
                ));
        long sourceDuplicatePairs = impressionsByPair.values().stream()
                .filter(count -> count > 1)
                .count();
        return new SourceDiagnostics(impressions.size(), (int) sourceDuplicatePairs);
    }

    private Set<Long> skillIds(List<Skill> skills) {
        return Optional.ofNullable(skills)
                .orElse(List.of())
                .stream()
                .map(Skill::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> categories(List<Skill> skills) {
        return Optional.ofNullable(skills)
                .orElse(List.of())
                .stream()
                .map(Skill::getCategory)
                .filter(Objects::nonNull)
                .map(SkillCategory::getName)
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private String toCsv(List<RealTrainingRowDto> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("executor_id,task_id,executor_city,task_city,view_label,matched_skills_count,")
                .append("skills_overlap_ratio,city_match,category_affinity,freshness_bonus,")
                .append("required_skill_count,executor_skill_count,exact_skill_cover,skill_gap_count,task_age_hours,")
                .append("baseline_score,")
                .append("activity_level,quality_score,apply_label,approve_label\n");

        for (RealTrainingRowDto row : rows) {
            csv.append(csvValue(row.getExecutorId())).append(',')
                    .append(row.getTaskId()).append(',')
                    .append(csvValue(row.getExecutorCity())).append(',')
                    .append(csvValue(row.getTaskCity())).append(',')
                    .append(row.getViewLabel()).append(',')
                    .append(row.getMatchedSkillsCount()).append(',')
                    .append(row.getSkillsOverlapRatio()).append(',')
                    .append(row.getCityMatch()).append(',')
                    .append(row.getCategoryAffinity()).append(',')
                    .append(row.getFreshnessBonus()).append(',')
                    .append(row.getRequiredSkillCount()).append(',')
                    .append(row.getExecutorSkillCount()).append(',')
                    .append(row.getExactSkillCover()).append(',')
                    .append(row.getSkillGapCount()).append(',')
                    .append(row.getTaskAgeHours()).append(',')
                    .append(row.getBaselineScore()).append(',')
                    .append(row.getActivityLevel()).append(',')
                    .append(row.getQualityScore()).append(',')
                    .append(row.getApplyLabel()).append(',')
                    .append(row.getApproveLabel()).append('\n');
        }

        return csv.toString();
    }

    private String csvValue(String value) {
        String safeValue = nullToEmpty(value);
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record PairKey(UUID executorId, Long taskId) {
    }

    private record DatasetDiagnostics(
            int uniqueExecutorTaskPairs,
            int positiveApplyPairs,
            int duplicatePairs,
            double positiveRateByPairs
    ) {
    }

    private record SourceDiagnostics(
            int sourceImpressionRows,
            int sourceDuplicateImpressionPairs
    ) {
    }
}
