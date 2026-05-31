package org.example.taskbid.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.component.JwtUtil;
import org.example.taskbid.dto.DevRecommenderScenarioResponse;
import org.example.taskbid.dto.DevSeedResponse;
import org.example.taskbid.dto.RealTrainingRowDto;
import org.example.taskbid.dto.TaskApplicantDto;
import org.example.taskbid.dto.TaskDto;
import org.example.taskbid.dto.TasksDto;
import org.example.taskbid.dto.TrainingDatasetExportResponse;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.Roles;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.RecommendationImpressionRepository;
import org.example.taskbid.repositiry.TaskApplicationRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.TaskViewEventRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevRecommenderScenarioService {

    static String RECOMMENDATION_SOURCE = "RECOMMENDATION";
    static String DIRECT_SOURCE = "DIRECT";
    static String EXPORT_PATH = "data/real_training_dataset.csv";
    static int SESSIONS_PER_EXECUTOR = 3;
    static int MAX_APPROVES = 35;
    static int MAX_COMPLETES = 12;
    static int MIN_POSITIVE_APPLY_LABELS = 50;

    DevSeedService devSeedService;
    TaskService taskService;
    RecommendationTrainingDatasetExportService trainingDatasetExportService;
    UserRepository userRepository;
    ProfileRepository profileRepository;
    TaskRepository taskRepository;
    TaskApplicationRepository taskApplicationRepository;
    RecommendationImpressionRepository recommendationImpressionRepository;
    TaskViewEventRepository taskViewEventRepository;
    JwtUtil jwtUtil;

    public DevRecommenderScenarioResponse runScenario() {
        Random random = new Random(42);
        long impressionsBefore = recommendationImpressionRepository.count();
        long viewsBefore = taskViewEventRepository.count();
        long appliesBefore = taskApplicationRepository.count();
        long doneBefore = taskRepository.findAll().stream()
                .filter(task -> task.getStatus().isCompleted())
                .count();

        DevSeedResponse seedResponse = devSeedService.seedRecommenderEpic();
        List<Profile> customers = demoProfiles(Roles.CUSTOMER);
        List<Profile> executors = demoProfiles(Roles.EXECUTOR);
        if (customers.isEmpty()) {
            throw new IllegalStateException("No demo customers found after seed.");
        }
        if (executors.isEmpty()) {
            throw new IllegalStateException("No demo executors found after seed.");
        }

        Map<java.util.UUID, BehaviorPattern> behaviorByExecutor = assignBehaviors(executors);
        Map<java.util.UUID, String> tokenByProfile = tokens(customers, executors);
        int recommendationRequests = 0;
        int recommendationsReturned = 0;
        int appliesCreated = 0;

        for (Profile executor : executors) {
            String token = tokenByProfile.get(executor.getId());
            BehaviorPattern behavior = behaviorByExecutor.get(executor.getId());

            for (int session = 0; session < SESSIONS_PER_EXECUTOR; session++) {
                List<TaskDto> recommendations = taskService.recommendTasks(token);
                recommendationRequests++;
                if (recommendations == null || recommendations.isEmpty()) {
                    continue;
                }
                recommendationsReturned += recommendations.size();

                int viewedInSession = 0;
                for (TaskDto recommendation : recommendations) {
                    if (recommendation.getId() == null || recommendation.getStatus() != TaskStatus.OPEN) {
                        continue;
                    }

                    Signals signals = signals(executor, recommendation);
                    if (random.nextDouble() > viewProbability(behavior, signals)) {
                        continue;
                    }

                    taskService.getTask(recommendation.getId(), token, RECOMMENDATION_SOURCE);
                    viewedInSession++;

                    if (taskApplicationRepository.existsByTask_IdAndExecutor_Id(recommendation.getId(), executor.getId())) {
                        continue;
                    }
                    if (random.nextDouble() <= applyProbability(behavior, signals)) {
                        taskService.applyToTask(recommendation.getId(), priceFor(behavior, signals), token);
                        appliesCreated++;
                    }
                    if (viewedInSession >= maxViewsPerSession(behavior)) {
                        break;
                    }
                }
            }
        }
        if (recommendationsReturned == 0) {
            throw new IllegalStateException("No recommendations were returned for demo executors. "
                    + "Check that the dev seed has OPEN tasks and recommendation-api is available.");
        }

        List<ApprovedTask> approvedTasks = approveCustomerTasks(customers, tokenByProfile, behaviorByExecutor, random);
        List<ApprovedTask> completedTasks = completeApprovedTasks(approvedTasks, tokenByProfile);

        TrainingDatasetExportResponse exportResponse = trainingDatasetExportService.exportCsv(EXPORT_PATH);
        List<RealTrainingRowDto> rows = trainingDatasetExportService.buildRows();
        if (rows.isEmpty()) {
            throw new IllegalStateException("Real training dataset export produced 0 rows. "
                    + "Check recommendation_impressions rows and exporter profile/task lookup.");
        }
        int positiveApplyLabels = (int) rows.stream()
                .filter(row -> row.getApplyLabel() != null && row.getApplyLabel() == 1)
                .count();
        if (positiveApplyLabels < MIN_POSITIVE_APPLY_LABELS) {
            throw new IllegalStateException("Real training dataset has only " + positiveApplyLabels
                    + " positive apply labels; expected at least " + MIN_POSITIVE_APPLY_LABELS
                    + ". Check recommendation-api mode, seed data, and available OPEN tasks.");
        }

        return DevRecommenderScenarioResponse.builder()
                .seedCreated(seedResponse.isCreated())
                .usersCreated(seedResponse.getUsersCreated())
                .tasksCreated(seedResponse.getTasksCreated())
                .totalDemoUsers(seedResponse.getTotalDemoUsers())
                .totalDemoTasks(seedResponse.getTotalDemoTasks())
                .behaviorPatterns(BehaviorPattern.names())
                .sessionsPerExecutor(SESSIONS_PER_EXECUTOR)
                .recommendationRequests(recommendationRequests)
                .recommendationImpressionsCreated((int) (recommendationImpressionRepository.count() - impressionsBefore))
                .taskViewsCreated((int) (taskViewEventRepository.count() - viewsBefore))
                .appliesCreated(appliesCreated)
                .appliesPersisted((int) (taskApplicationRepository.count() - appliesBefore))
                .approvesDone(approvedTasks.size())
                .tasksDone((int) (taskRepository.findAll().stream()
                        .filter(task -> task.getStatus().isCompleted())
                        .count() - doneBefore))
                .completesDone(completedTasks.size())
                .exportedCsvPath(exportResponse.getPath())
                .exportedRows(exportResponse.getRows())
                .positiveApplyLabels(positiveApplyLabels)
                .build();
    }

    private List<ApprovedTask> approveCustomerTasks(
            List<Profile> customers,
            Map<java.util.UUID, String> tokenByProfile,
            Map<java.util.UUID, BehaviorPattern> behaviorByExecutor,
            Random random
    ) {
        List<ApprovedTask> approvedTasks = new ArrayList<>();

        for (Profile customer : customers) {
            if (approvedTasks.size() >= MAX_APPROVES) {
                break;
            }
            String customerToken = tokenByProfile.get(customer.getId());
            List<TasksDto> tasks = new ArrayList<>(taskService.getTasksByUser(customerToken));
            tasks.sort(Comparator.comparing(TasksDto::getId));

            for (TasksDto taskSummary : tasks) {
                if (approvedTasks.size() >= MAX_APPROVES) {
                    break;
                }
                TaskDto task = taskService.getTask(taskSummary.getId(), customerToken, DIRECT_SOURCE);
                if (task.getStatus() != TaskStatus.OPEN || task.getApplicants() == null || task.getApplicants().isEmpty()) {
                    continue;
                }

                TaskApplicantDto applicant = chooseApplicant(task.getApplicants(), behaviorByExecutor, random);
                double approveProbability = approvalProbability(behaviorByExecutor.get(applicant.getProfileId()));
                if (random.nextDouble() > approveProbability) {
                    continue;
                }

                taskService.approveApplication(task.getId(), applicant.getApplicationId(), customerToken);
                approvedTasks.add(new ApprovedTask(task.getId(), applicant.getProfileId(), customer.getId()));
            }
        }

        return approvedTasks;
    }

    private List<ApprovedTask> completeApprovedTasks(List<ApprovedTask> approvedTasks, Map<java.util.UUID, String> tokenByProfile) {
        List<ApprovedTask> completed = new ArrayList<>();
        for (ApprovedTask approvedTask : approvedTasks) {
            if (completed.size() >= MAX_COMPLETES) {
                break;
            }

            String executorToken = tokenByProfile.get(approvedTask.executorId());
            String customerToken = tokenByProfile.get(approvedTask.customerId());
            if (executorToken == null || customerToken == null) {
                continue;
            }

            taskService.startWork(approvedTask.taskId(), executorToken);
            taskService.finishWork(approvedTask.taskId(), executorToken);
            taskService.completeTask(approvedTask.taskId(), customerToken);
            completed.add(approvedTask);
        }
        return completed;
    }

    private TaskApplicantDto chooseApplicant(
            List<TaskApplicantDto> applicants,
            Map<java.util.UUID, BehaviorPattern> behaviorByExecutor,
            Random random
    ) {
        return applicants.stream()
                .max(Comparator.comparingDouble(applicant ->
                        approvalScore(behaviorByExecutor.get(applicant.getProfileId()), random)))
                .orElse(applicants.get(0));
    }

    private double approvalScore(BehaviorPattern behavior, Random random) {
        return approvalProbability(behavior) + random.nextDouble() * 0.2;
    }

    private double approvalProbability(BehaviorPattern behavior) {
        if (behavior == BehaviorPattern.HIGH_QUALITY) {
            return 0.9;
        }
        if (behavior == BehaviorPattern.ACTIVE_SELECTIVE) {
            return 0.72;
        }
        if (behavior == BehaviorPattern.LOCAL_ONLY) {
            return 0.68;
        }
        if (behavior == BehaviorPattern.ACTIVE_BROAD) {
            return 0.58;
        }
        if (behavior == BehaviorPattern.FRESHNESS_DRIVEN) {
            return 0.55;
        }
        return 0.35;
    }

    private double viewProbability(BehaviorPattern behavior, Signals signals) {
        return switch (behavior) {
            case ACTIVE_SELECTIVE -> clamp(0.35 + signals.overlapRatio() * 0.45 + signals.cityMatch() * 0.15);
            case ACTIVE_BROAD -> clamp(0.70 + signals.categoryAffinity() * 0.15);
            case LOCAL_ONLY -> signals.cityMatch() > 0 ? clamp(0.65 + signals.overlapRatio() * 0.25) : 0.10;
            case FRESHNESS_DRIVEN -> clamp(0.25 + signals.freshness() * 0.55 + signals.overlapRatio() * 0.20);
            case LOW_ACTIVITY -> clamp(0.18 + signals.overlapRatio() * 0.20);
            case HIGH_QUALITY -> clamp(0.45 + signals.overlapRatio() * 0.35 + signals.cityMatch() * 0.10);
        };
    }

    private double applyProbability(BehaviorPattern behavior, Signals signals) {
        if (signals.matchedSkills() == 0) {
            return switch (behavior) {
                case ACTIVE_BROAD -> 0.06;
                default -> 0.01;
            };
        }

        return switch (behavior) {
            case ACTIVE_SELECTIVE -> clamp(0.10 + signals.overlapRatio() * 0.60 + signals.cityMatch() * 0.10);
            case ACTIVE_BROAD -> clamp(0.28 + signals.overlapRatio() * 0.35 + signals.categoryAffinity() * 0.15);
            case LOCAL_ONLY -> signals.cityMatch() > 0 ? clamp(0.22 + signals.overlapRatio() * 0.45) : 0.03;
            case FRESHNESS_DRIVEN -> clamp(0.12 + signals.freshness() * 0.40 + signals.overlapRatio() * 0.25);
            case LOW_ACTIVITY -> clamp(0.05 + signals.overlapRatio() * 0.18);
            case HIGH_QUALITY -> clamp(0.18 + signals.overlapRatio() * 0.55 + signals.cityMatch() * 0.10);
        };
    }

    private int maxViewsPerSession(BehaviorPattern behavior) {
        return switch (behavior) {
            case ACTIVE_BROAD -> 12;
            case ACTIVE_SELECTIVE, HIGH_QUALITY -> 9;
            case FRESHNESS_DRIVEN, LOCAL_ONLY -> 7;
            case LOW_ACTIVITY -> 4;
        };
    }

    private String priceFor(BehaviorPattern behavior, Signals signals) {
        int base = switch (behavior) {
            case HIGH_QUALITY -> 140;
            case ACTIVE_SELECTIVE -> 115;
            case LOCAL_ONLY -> 105;
            case FRESHNESS_DRIVEN -> 100;
            case ACTIVE_BROAD -> 90;
            case LOW_ACTIVITY -> 80;
        };
        return (base + signals.matchedSkills() * 10) + " BYN";
    }

    private Signals signals(Profile executor, TaskDto task) {
        Set<Long> executorSkillIds = skillIds(executor.getSkills());
        Set<Long> taskSkillIds = skillIds(task.getRequiredSkills());
        Set<Long> matched = new HashSet<>(executorSkillIds);
        matched.retainAll(taskSkillIds);

        Set<String> executorCategories = categories(executor.getSkills());
        Set<String> taskCategories = categories(task.getRequiredSkills());
        Set<String> matchedCategories = new HashSet<>(executorCategories);
        matchedCategories.retainAll(taskCategories);

        double overlap = taskSkillIds.isEmpty() ? 0.0 : (double) matched.size() / taskSkillIds.size();
        double categoryAffinity = taskCategories.isEmpty() ? 0.0 : (double) matchedCategories.size() / taskCategories.size();
        double cityMatch = executor.getCity() != null && task.getCity() != null
                && executor.getCity().trim().equalsIgnoreCase(task.getCity().trim()) ? 1.0 : 0.0;
        double freshness = freshness(task.getCreatedAt());

        return new Signals(matched.size(), overlap, cityMatch, categoryAffinity, freshness);
    }

    private double freshness(LocalDateTime createdAt) {
        if (createdAt == null) {
            return 0.0;
        }
        long ageHours = Duration.between(createdAt, LocalDateTime.now()).toHours();
        if (ageHours <= 24) {
            return 1.0;
        }
        if (ageHours <= 72) {
            return 0.5;
        }
        return 0.0;
    }

    private Set<Long> skillIds(List<Skill> skills) {
        Set<Long> ids = new HashSet<>();
        if (skills == null) {
            return ids;
        }
        for (Skill skill : skills) {
            if (skill.getId() != null) {
                ids.add(skill.getId());
            }
        }
        return ids;
    }

    private Set<String> categories(List<Skill> skills) {
        Set<String> result = new HashSet<>();
        if (skills == null) {
            return result;
        }
        for (Skill skill : skills) {
            if (skill.getCategory() != null && skill.getCategory().getName() != null) {
                result.add(skill.getCategory().getName().trim().toLowerCase());
            }
        }
        return result;
    }

    private List<Profile> demoProfiles(Roles role) {
        return userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().startsWith("demo."))
                .map(user -> profileRepository.findByUser(user).orElse(null))
                .filter(Objects::nonNull)
                .filter(profile -> profile.getRole() == role)
                .sorted(Comparator.comparing(profile -> profile.getUser().getEmail()))
                .toList();
    }

    private Map<java.util.UUID, BehaviorPattern> assignBehaviors(List<Profile> executors) {
        Map<java.util.UUID, BehaviorPattern> result = new HashMap<>();
        BehaviorPattern[] patterns = BehaviorPattern.values();
        for (int index = 0; index < executors.size(); index++) {
            result.put(executors.get(index).getId(), patterns[index % patterns.length]);
        }
        return result;
    }

    private Map<java.util.UUID, String> tokens(List<Profile> customers, List<Profile> executors) {
        Map<java.util.UUID, String> result = new HashMap<>();
        for (Profile customer : customers) {
            result.put(customer.getId(), jwtUtil.generateToken(Roles.CUSTOMER.name(), customer.getUser().getEmail()));
        }
        for (Profile executor : executors) {
            result.put(executor.getId(), jwtUtil.generateToken(Roles.EXECUTOR.name(), executor.getUser().getEmail()));
        }
        return result;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private enum BehaviorPattern {
        ACTIVE_SELECTIVE,
        ACTIVE_BROAD,
        LOCAL_ONLY,
        FRESHNESS_DRIVEN,
        LOW_ACTIVITY,
        HIGH_QUALITY;

        static List<String> names() {
            List<String> names = new ArrayList<>();
            for (BehaviorPattern pattern : values()) {
                names.add(pattern.name());
            }
            return names;
        }
    }

    private record Signals(
            int matchedSkills,
            double overlapRatio,
            double cityMatch,
            double categoryAffinity,
            double freshness
    ) {
    }

    private record ApprovedTask(Long taskId, java.util.UUID executorId, java.util.UUID customerId) {
    }
}
