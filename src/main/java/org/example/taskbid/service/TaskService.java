package org.example.taskbid.service;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
import org.example.taskbid.dto.CreateTaskRequest;
import org.example.taskbid.dto.TaskDto;
import org.example.taskbid.dto.TasksDto;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.SkillRepository;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.example.taskbid.component.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.taskbid.ml.MlRecommendationClient;
import org.example.taskbid.dto.MlProfilePayload;
import org.example.taskbid.dto.MlRecommendationRequest;
import org.example.taskbid.dto.MlSkillPayload;
import org.example.taskbid.dto.MlTaskPayload;
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
    public TaskDto getTask(Long id) {

        Task task = taskRepository.findTaskById(id);

        return TaskDto.builder()
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .city(task.getCity())
                .createdAt(task.getCreatedAt())
                .requiredSkills(task.getRequiredSkills())
                .build();
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
                .experience(profile.getExperience())
                .workRadiusKm(profile.getWorkRadiusKm())
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
}




