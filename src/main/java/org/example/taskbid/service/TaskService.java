package org.example.taskbid.service;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
import org.example.taskbid.dto.*;
import org.example.taskbid.entity.*;
import org.example.taskbid.entity.enums.ApplicationStatus;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.repositiry.*;
import org.example.taskbid.component.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.taskbid.ml.MlRecommendationClient;
import org.example.taskbid.entity.enums.Roles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TaskService {

    TaskRepository taskRepository;
    SkillRepository skillRepository;
    ProfileRepository profileRepository;
    UserRepository userRepository;
    JwtUtil jwtUtil;
    MlRecommendationClient mlRecommendationClient;
    TaskApplicationRepository taskApplicationRepository;
    RecommendationImpressionService recommendationImpressionService;
    TaskViewEventService taskViewEventService;
    TaskReviewService taskReviewService;

    @Transactional
    public void createTask(CreateTaskRequest req, String token) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.getUsersByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found by token email");
        }

        Profile authorProfile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found for user"));

        List<Skill> skills = List.of();
        if (req.getSkillIds() != null && !req.getSkillIds().isEmpty()) {
            skills = skillRepository.findAllById(req.getSkillIds());
        }

        Task task = Task.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .city(req.getCity())
                .status(TaskStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .author(authorProfile)
                .requiredSkills(skills)
                .build();

        Task saved = taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<TasksDto> getTasksByUser(String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Profile> optProfile = profileRepository.findByUser(user);

        Profile profile = optProfile.orElse(new Profile());

        List<Task> tasks = taskRepository.findAllByAuthor(profile);

        return tasks.stream()
                .map(task -> {
                    TasksDto dto = new TasksDto();
                    dto.setId(task.getId());
                    dto.setTitle(task.getTitle());
                    dto.setStatus(task.getStatus().publicName());
                    dto.setBeginDate(task.getCreatedAt().toString());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(Long id, String token) {
        return getTask(id, token, null);
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(Long id, String token, String source) {
        Task task = taskRepository.findTaskById(id);
        Profile viewerProfile = resolveProfile(token);
        taskViewEventService.logView(viewerProfile, task, source);
        boolean currentUserApplied = viewerProfile != null
                && Roles.EXECUTOR.equals(viewerProfile.getRole())
                && taskApplicationRepository.existsByTask_IdAndExecutor_Id(task.getId(), viewerProfile.getId());

        TaskDto.TaskDtoBuilder b = TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .readyForCompletion(task.getStatus().isReadyForCompletion())
                .city(task.getCity())
                .createdAt(task.getCreatedAt())
                .requiredSkills(task.getRequiredSkills())
                .currentUserApplied(currentUserApplied);

        applyReviewMetadata(b, task, viewerProfile);

        if (task.getStatus() != TaskStatus.OPEN && task.getExecutor() != null) {
            b.executor(toExecutorDto(task.getExecutor(), null));
            return b.build();
        }

        // если OPEN — applicants только автору
        if (task.getStatus() == TaskStatus.OPEN && viewerProfile != null) {
            if (task.getAuthor().getId().equals(viewerProfile.getId())) {
                var apps = taskApplicationRepository.findAllByTask_Id(task.getId());

                b.applicants(apps.stream().map(a -> {
                    Profile p = a.getExecutor();
                    Optional<TaskApplication> taskApplication = taskApplicationRepository
                            .findByTask_IdAndExecutor_Id(id, p.getId());
                    return toExecutorDto(p, a)
                            .toBuilder()
                            .price(taskApplication.get().getPrice())
                            .build();
                }).toList());
            }
        }

        return b.build();
    }

    private void applyReviewMetadata(TaskDto.TaskDtoBuilder builder, Task task, Profile viewerProfile) {
        Optional<TaskReviewResponse> review = Optional.ofNullable(taskReviewService.findTaskReview(task.getId()))
                .orElse(Optional.empty());
        builder.reviewExists(review.isPresent())
                .reviewAllowed(taskReviewService.canCurrentUserReview(task, viewerProfile))
                .review(review.orElse(null));
    }

    private TaskApplicantDto toExecutorDto(Profile profile, TaskApplication application) {
        ExecutorRatingDto rating = taskReviewService.ratingForExecutor(profile);
        return TaskApplicantDto.builder()
                .applicationId(application == null ? null : application.getId())
                .profileId(profile.getId())
                .username(profile.getUser().getUsername())
                .city(profile.getCity())
                .description(profile.getDescription())
                .price(application == null ? null : application.getPrice())
                .averageRating(rating.getAverageRating())
                .reviewsCount(rating.getReviewsCount())
                .skills(profile.getSkills())
                .createdAt(application == null ? null : application.getCreatedAt())
                .build();
    }

    private Profile resolveProfile(String token) {
        if (token == null) {
            return null;
        }

        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email).orElse(null);
        return user == null ? null : profileRepository.findByUser(user).orElse(null);
    }


    @Transactional
    public List<TaskDto> recommendTasks(String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!Roles.EXECUTOR.equals(profile.getRole())) {
            throw new RuntimeException("Only executors can request recommendations");
        }

        List<Task> openTasks = taskRepository.findAllByStatus(TaskStatus.OPEN)
                .stream()
                .filter(task -> !Objects.equals(task.getAuthor().getId(), profile.getId()))
                .filter(task -> !taskApplicationRepository.existsByTask_IdAndExecutor_Id(task.getId(), profile.getId()))
                .toList();

        log.debug("Candidate recommendation tasks count: {}", openTasks.size());

        MlRecommendationRequest request = MlRecommendationRequest.builder()
                .executor(buildProfilePayload(profile))
                .tasks(openTasks.stream().map(this::buildTaskPayload).toList())
                .build();

        log.info("Requesting recommendations for executor={}, candidates={}", profile.getId(), openTasks.size());
        MlRecommendationResponse recommendationResponse = mlRecommendationClient.getRecommendationResponse(request);
        List<Long> recommendedIds = Optional.ofNullable(recommendationResponse.getRecommendedTaskIds())
                .orElse(List.of());
        log.debug("Recommendation ids returned by ML service: {}", recommendedIds);
        Map<Long, Task> tasksById = openTasks.stream()
                .collect(Collectors.toMap(Task::getId, task -> task));

        Stream<Task> recommendedTasks = recommendedIds.isEmpty()
                ? openTasks.stream()
                : recommendedIds.stream()
                .map(tasksById::get)
                .filter(Objects::nonNull);

        List<Task> returnedTasks = recommendedTasks.toList();
        recommendationImpressionService.logImpressions(profile, returnedTasks, recommendationResponse);

        return returnedTasks.stream()
                .map(this::toTaskDto)
                .toList();
    }

    private MlProfilePayload buildProfilePayload(Profile profile) {
        List<MlSkillPayload> skills = Optional.ofNullable(profile.getSkills())
                .orElse(List.of())
                .stream()
                .map(this::toSkillPayload)
                .toList();

        return MlProfilePayload.builder()
                .id(profile.getId())
                .city(profile.getCity())
                .skills(skills)
                .build();
    }

    private MlTaskPayload buildTaskPayload(Task task) {
        List<MlSkillPayload> skills = Optional.ofNullable(task.getRequiredSkills())
                .orElse(List.of())
                .stream()
                .map(this::toSkillPayload)
                .toList();

        return MlTaskPayload.builder()
                .id(task.getId())
                .title(task.getTitle())
                .city(task.getCity())
                .createdAt(task.getCreatedAt())
                .requiredSkills(skills)
                .build();
    }

    private MlSkillPayload toSkillPayload(Skill skill) {
        String categoryName = Optional.ofNullable(skill.getCategory())
                .map(category -> category.getName())
                .orElse(null);

        return MlSkillPayload.builder()
                .id(skill.getId())
                .name(skill.getName())
                .category(categoryName)
                .build();
    }

    private TaskDto toTaskDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .readyForCompletion(task.getStatus().isReadyForCompletion())
                .city(task.getCity())
                .createdAt(task.getCreatedAt())
                .requiredSkills(task.getRequiredSkills())
                .build();
    }

    @Transactional
    public void deleteTask(Long taskId, String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        Task task = taskRepository.findTaskById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found");
        }

        if (task.getAuthor() == null || !Objects.equals(task.getAuthor().getId(), profile.getId())) {
            throw new RuntimeException("You are not author of this task");
        }

        if (task.getStatus() != TaskStatus.OPEN) {
            throw new RuntimeException("Only OPEN tasks can be deleted");
        }

        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getAllOpenTasksExceptMine(String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!Roles.EXECUTOR.equals(profile.getRole())) {
            throw new RuntimeException("Only executors can view all tasks");
        }

        List<TaskDto> tasks = taskRepository.findAllByStatus(TaskStatus.OPEN).stream()
                .filter(t -> !Objects.equals(t.getAuthor().getId(), profile.getId()))
                .map(this::toTaskDto)
                .toList();

        log.debug("Open tasks returned to executor={}, count={}", profile.getId(), tasks.size());

        return tasks;

    }

    @Transactional
    public void applyToTask(Long taskId, String price, String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile executor = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!Roles.EXECUTOR.equals(executor.getRole())) {
            throw new RuntimeException("Only executors can apply");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getStatus() != TaskStatus.OPEN) {
            throw new RuntimeException("Task is not OPEN");
        }

        if (Objects.equals(task.getAuthor().getId(), executor.getId())) {
            throw new RuntimeException("Cannot apply to your own task");
        }

        if (taskApplicationRepository.existsByTask_IdAndExecutor_Id(taskId, executor.getId())) {
            throw new RuntimeException("Already applied");
        }

        if (price == null || price.trim().isEmpty()) {
            throw new RuntimeException("Price is required");
        }

        TaskApplication app = TaskApplication.builder()
                .task(task)
                .executor(executor)
                .status(ApplicationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .price(price.trim()) // ✅ ВАЖНО
                .build();

        taskApplicationRepository.save(app);
    }


    @Transactional(readOnly = true)
    public List<TaskApplicationsCountDto> getMyTasksApplicationsCounts(String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return taskApplicationRepository.countByAuthorTasks(profile.getId());
    }

    @Transactional
    public void approveApplication(Long taskId, Long appId, String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile customerProfile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getAuthor().getId().equals(customerProfile.getId())) {
            throw new RuntimeException("You are not the author of this task");
        }

        if (task.getStatus() != TaskStatus.OPEN) {
            throw new RuntimeException("Task is not OPEN");
        }

        TaskApplication app = taskApplicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!app.getTask().getId().equals(taskId)) {
            throw new RuntimeException("Application does not belong to this task");
        }

        // 1) назначаем исполнителя
        task.setExecutor(app.getExecutor());

        // 2) переводим задачу в работу
        task.setStatus(TaskStatus.ASSIGNED);
        taskRepository.save(task);

        app.setStatus(ApplicationStatus.APPROVED);
        taskApplicationRepository.save(app);

        taskApplicationRepository.updatePendingStatusesByTaskIdAndIdNot(taskId, appId, ApplicationStatus.REJECTED);
    }

    @Transactional(readOnly = true)
    public List<MyApplicationDto> getMyApplications(String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!Roles.EXECUTOR.equals(profile.getRole())) {
            throw new RuntimeException("Only executors can view their applications");
        }

        return taskApplicationRepository.findMyApplications(profile.getId());
    }

    @Transactional
    public void startWork(Long taskId, String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile executor = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (executor.getRole() != Roles.EXECUTOR) {
            throw new RuntimeException("Only executors can start work");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getExecutor() == null || !task.getExecutor().getId().equals(executor.getId())) {
            throw new RuntimeException("You are not assigned executor for this task");
        }

        if (!task.getStatus().isAssigned()) {
            throw new RuntimeException("Task is not ASSIGNED");
        }

        TaskApplication app = taskApplicationRepository
                .findByTask_IdAndExecutor_Id(taskId, executor.getId())
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!app.getStatus().isApproved()) {
            throw new RuntimeException("Application is not approved");
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
    }

    @Transactional
    public void finishWork(Long taskId, String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile executor = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (executor.getRole() != Roles.EXECUTOR) {
            throw new RuntimeException("Only executors can finish work");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (task.getExecutor() == null || !task.getExecutor().getId().equals(executor.getId())) {
            throw new RuntimeException("You are not assigned executor for this task");
        }

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new RuntimeException("Task is not IN_PROGRESS");
        }

        TaskApplication app = taskApplicationRepository
                .findByTask_IdAndExecutor_Id(taskId, executor.getId())
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!app.getStatus().isApproved()) {
            throw new RuntimeException("Application is not approved");
        }

        task.setStatus(TaskStatus.READY_FOR_ACCEPTANCE);
        taskRepository.save(task);
    }


    @Transactional
    public void completeTask(Long taskId, String token) {
        String email = jwtUtil.extractEmail(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Profile customer = profileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!task.getAuthor().getId().equals(customer.getId())) {
            throw new RuntimeException("You are not the author of this task");
        }

        if (task.getStatus() != TaskStatus.READY_FOR_ACCEPTANCE) {
            throw new RuntimeException("Task is not ready for acceptance");
        }

        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
    }


}
