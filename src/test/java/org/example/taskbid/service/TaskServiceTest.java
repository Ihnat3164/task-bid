package org.example.taskbid.service;

import org.example.taskbid.component.JwtUtil;
import org.example.taskbid.dto.MlRecommendationItemDto;
import org.example.taskbid.dto.MlRecommendationRequest;
import org.example.taskbid.dto.MlRecommendationResponse;
import org.example.taskbid.dto.TaskDto;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.SkillCategory;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.TaskApplication;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.ApplicationStatus;
import org.example.taskbid.entity.enums.Roles;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.ml.MlRecommendationClient;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.SkillRepository;
import org.example.taskbid.repositiry.TaskApplicationRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;
    @Mock
    SkillRepository skillRepository;
    @Mock
    ProfileRepository profileRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    JwtUtil jwtUtil;
    @Mock
    MlRecommendationClient mlRecommendationClient;
    @Mock
    TaskApplicationRepository taskApplicationRepository;
    @Mock
    RecommendationImpressionService recommendationImpressionService;
    @Mock
    TaskViewEventService taskViewEventService;
    @Mock
    TaskReviewService taskReviewService;

    @InjectMocks
    TaskService taskService;

    User executorUser;
    Profile executor;
    Profile customer;

    @BeforeEach
    void setUp() {
        executorUser = user("executor@test.local");
        executor = profile(UUID.randomUUID(), executorUser, Roles.EXECUTOR, "Минск", List.of(skill(1L, "Python", "Backend")));
        customer = profile(UUID.randomUUID(), user("customer@test.local"), Roles.CUSTOMER, "Минск", List.of());
    }

    @Test
    void recommendTasksKeepsOnlyOpenNotOwnAndNotAlreadyAppliedTasks() {
        Task ownTask = task(10L, customer, TaskStatus.OPEN);
        ownTask.setAuthor(executor);
        Task alreadyAppliedTask = task(20L, customer, TaskStatus.OPEN);
        Task candidate = task(30L, customer, TaskStatus.OPEN);

        when(jwtUtil.extractEmail("executor-token")).thenReturn(executorUser.getEmail());
        when(userRepository.findByEmail(executorUser.getEmail())).thenReturn(Optional.of(executorUser));
        when(profileRepository.findByUser(executorUser)).thenReturn(Optional.of(executor));
        when(taskRepository.findAllByStatus(TaskStatus.OPEN)).thenReturn(List.of(ownTask, alreadyAppliedTask, candidate));
        when(taskApplicationRepository.existsByTask_IdAndExecutor_Id(20L, executor.getId())).thenReturn(true);
        when(taskApplicationRepository.existsByTask_IdAndExecutor_Id(30L, executor.getId())).thenReturn(false);
        when(mlRecommendationClient.getRecommendationResponse(any())).thenReturn(MlRecommendationResponse.builder()
                .recommendedTaskIds(List.of(30L))
                .recommendations(List.of(MlRecommendationItemDto.builder()
                        .taskId(30L)
                        .score(0.91)
                        .reasons(List.of("SKILL_MATCH"))
                        .build()))
                .mode("ml")
                .modelVersion("apply-logreg-v1")
                .scoreType("apply_probability")
                .build());

        List<TaskDto> result = taskService.recommendTasks("executor-token");

        assertThat(result).extracting(TaskDto::getId).containsExactly(30L);

        ArgumentCaptor<MlRecommendationRequest> requestCaptor = ArgumentCaptor.forClass(MlRecommendationRequest.class);
        verify(mlRecommendationClient).getRecommendationResponse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTasks()).extracting("id").containsExactly(30L);
        verify(recommendationImpressionService).logImpressions(eq(executor), eq(List.of(candidate)), any());
    }

    @Test
    void getTaskMarksCurrentUserAppliedAndLogsViewSource() {
        Task task = task(40L, customer, TaskStatus.OPEN);

        when(taskRepository.findTaskById(40L)).thenReturn(task);
        when(jwtUtil.extractEmail("executor-token")).thenReturn(executorUser.getEmail());
        when(userRepository.findByEmail(executorUser.getEmail())).thenReturn(Optional.of(executorUser));
        when(profileRepository.findByUser(executorUser)).thenReturn(Optional.of(executor));
        when(taskApplicationRepository.existsByTask_IdAndExecutor_Id(40L, executor.getId())).thenReturn(true);
        when(taskReviewService.findTaskReview(40L)).thenReturn(Optional.empty());
        when(taskReviewService.canCurrentUserReview(task, executor)).thenReturn(false);

        TaskDto dto = taskService.getTask(40L, "executor-token", "RECOMMENDATION");

        assertThat(dto.isCurrentUserApplied()).isTrue();
        verify(taskViewEventService).logView(executor, task, "RECOMMENDATION");
    }

    @Test
    void applyToTaskRejectsDuplicateApplication() {
        Task task = task(50L, customer, TaskStatus.OPEN);

        when(jwtUtil.extractEmail("executor-token")).thenReturn(executorUser.getEmail());
        when(userRepository.findByEmail(executorUser.getEmail())).thenReturn(Optional.of(executorUser));
        when(profileRepository.findByUser(executorUser)).thenReturn(Optional.of(executor));
        when(taskRepository.findById(50L)).thenReturn(Optional.of(task));
        when(taskApplicationRepository.existsByTask_IdAndExecutor_Id(50L, executor.getId())).thenReturn(true);

        assertThatThrownBy(() -> taskService.applyToTask(50L, "100 BYN", "executor-token"))
                .hasMessage("Already applied");

        verify(taskApplicationRepository, never()).save(any());
    }

    @Test
    void approveStartFinishAndCompleteFlowUsesExpectedStatuses() {
        Task task = task(60L, customer, TaskStatus.OPEN);
        TaskApplication application = TaskApplication.builder()
                .id(600L)
                .task(task)
                .executor(executor)
                .status(ApplicationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .price("100 BYN")
                .build();

        when(jwtUtil.extractEmail("customer-token")).thenReturn(customer.getUser().getEmail());
        when(jwtUtil.extractEmail("executor-token")).thenReturn(executorUser.getEmail());
        when(userRepository.findByEmail(customer.getUser().getEmail())).thenReturn(Optional.of(customer.getUser()));
        when(userRepository.findByEmail(executorUser.getEmail())).thenReturn(Optional.of(executorUser));
        when(profileRepository.findByUser(customer.getUser())).thenReturn(Optional.of(customer));
        when(profileRepository.findByUser(executorUser)).thenReturn(Optional.of(executor));
        when(taskRepository.findById(60L)).thenReturn(Optional.of(task));
        when(taskApplicationRepository.findById(600L)).thenReturn(Optional.of(application));
        when(taskApplicationRepository.findByTask_IdAndExecutor_Id(60L, executor.getId())).thenReturn(Optional.of(application));

        taskService.approveApplication(60L, 600L, "customer-token");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(task.getExecutor()).isEqualTo(executor);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        verify(taskApplicationRepository).updatePendingStatusesByTaskIdAndIdNot(60L, 600L, ApplicationStatus.REJECTED);

        taskService.startWork(60L, "executor-token");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        taskService.finishWork(60L, "executor-token");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.READY_FOR_ACCEPTANCE);

        taskService.completeTask(60L, "customer-token");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    private User user(String email) {
        return User.builder()
                .id(Math.abs((long) email.hashCode()))
                .email(email)
                .username(email)
                .password("hash")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Profile profile(UUID id, User user, Roles role, String city, List<Skill> skills) {
        return Profile.builder()
                .id(id)
                .user(user)
                .role(role)
                .city(city)
                .skills(skills)
                .build();
    }

    private Task task(Long id, Profile author, TaskStatus status) {
        return Task.builder()
                .id(id)
                .title("Task " + id)
                .description("Description")
                .city("Минск")
                .status(status)
                .createdAt(LocalDateTime.now().minusHours(2))
                .author(author)
                .requiredSkills(List.of(skill(1L, "Python", "Backend")))
                .build();
    }

    private Skill skill(Long id, String name, String categoryName) {
        SkillCategory category = new SkillCategory();
        category.setId(id);
        category.setName(categoryName);

        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        skill.setCategory(category);
        return skill;
    }
}
