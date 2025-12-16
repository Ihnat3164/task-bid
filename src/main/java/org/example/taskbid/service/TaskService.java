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
                    dto.setStatus(task.getStatus().name());
                    dto.setBeginDate(task.getCreatedAt().toString());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(Long id, String token) {
        Task task = taskRepository.findTaskById(id);

        TaskDto.TaskDtoBuilder b = TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .city(task.getCity())
                .createdAt(task.getCreatedAt())
                .requiredSkills(task.getRequiredSkills());

        if (task.getStatus() != TaskStatus.OPEN && task.getExecutor() != null) {
            Profile ex = task.getExecutor();
            b.executor(TaskApplicantDto.builder()
                    .profileId(ex.getId())
                    .username(ex.getUser().getUsername())
                    .city(ex.getCity())
                    .description(ex.getDescription())
                    .skills(ex.getSkills())
                    .build()
            );
            return b.build();
        }

        // если OPEN — applicants только автору
        if (task.getStatus() == TaskStatus.OPEN && token != null) {
            String email = jwtUtil.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);
            Profile me = user == null ? null : profileRepository.findByUser(user).orElse(null);

            if (me != null && task.getAuthor().getId().equals(me.getId())) {
                var apps = taskApplicationRepository.findAllByTask_Id(task.getId());

                b.applicants(apps.stream().map(a -> {
                    Profile p = a.getExecutor();
                    Optional<TaskApplication> taskApplication = taskApplicationRepository
                            .findByTask_IdAndExecutor_Id(id, p.getId());
                    return TaskApplicantDto.builder()
                            .applicationId(a.getId())
                            .profileId(p.getId())
                            .price(taskApplication.get().getPrice())
                            .username(p.getUser().getUsername())
                            .city(p.getCity())
                            .description(p.getDescription())
                            .skills(p.getSkills())
                            .createdAt(a.getCreatedAt())
                            .build();
                }).toList());
            }
        }

        return b.build();
    }


    @Transactional(readOnly = true)
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
                .toList();

        log.info("Tasks: {}", openTasks);

        MlRecommendationRequest request = MlRecommendationRequest.builder()
                .executor(buildProfilePayload(profile))
                .tasks(openTasks.stream().map(this::buildTaskPayload).toList())
                .build();

        log.info("Request ml: {}", request);
        List<Long> recommendedIds = mlRecommendationClient.getRecommendations(request);
        log.info("Реки ml: {}", recommendedIds);
        Map<Long, Task> tasksById = openTasks.stream()
                .collect(Collectors.toMap(Task::getId, task -> task));

        Stream<Task> recommendedTasks = recommendedIds.isEmpty()
                ? openTasks.stream()
                : recommendedIds.stream()
                .map(tasksById::get)
                .filter(Objects::nonNull);

        return recommendedTasks
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

        log.info("Tasks: {}", tasks);

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
        task.setStatus(TaskStatus.READY_FOR_WORK);
        taskRepository.save(task);

        app.setStatus(ApplicationStatus.ACCEPTED);
        taskApplicationRepository.save(app);

        taskApplicationRepository.deleteAllByTask_IdAndIdNot(taskId, appId);
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

        if (task.getStatus() != TaskStatus.READY_FOR_WORK) {
            throw new RuntimeException("Task is not READY_FOR_WORK");
        }

        TaskApplication app = taskApplicationRepository
                .findByTask_IdAndExecutor_Id(taskId, executor.getId())
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (app.getStatus() != ApplicationStatus.ACCEPTED ) {
            throw new RuntimeException("Application is not accepted");
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

        if (app.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new RuntimeException("Application is not accepted");
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

        task.setStatus(TaskStatus.DONE);
        taskRepository.save(task);
    }


}




