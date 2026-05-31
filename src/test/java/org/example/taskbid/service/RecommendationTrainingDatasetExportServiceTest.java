package org.example.taskbid.service;

import org.example.taskbid.dto.RealTrainingRowDto;
import org.example.taskbid.dto.TrainingDatasetExportResponse;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.RecommendationImpression;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.SkillCategory;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.TaskApplication;
import org.example.taskbid.entity.TaskViewEvent;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.ApplicationStatus;
import org.example.taskbid.entity.enums.Roles;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.entity.enums.TaskViewSource;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.RecommendationImpressionRepository;
import org.example.taskbid.repositiry.TaskApplicationRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.TaskViewEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationTrainingDatasetExportServiceTest {

    @Mock
    RecommendationImpressionRepository recommendationImpressionRepository;
    @Mock
    TaskApplicationRepository taskApplicationRepository;
    @Mock
    TaskViewEventRepository taskViewEventRepository;
    @Mock
    ProfileRepository profileRepository;
    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    RecommendationTrainingDatasetExportService exportService;

    @TempDir
    Path tempDir;

    @Test
    void buildRowsDeduplicatesPairAndAttributesApplyToLatestImpressionBeforeApply() throws Exception {
        LocalDateTime taskCreatedAt = LocalDateTime.of(2026, 4, 30, 10, 0);
        LocalDateTime firstImpressionAt = LocalDateTime.of(2026, 5, 1, 9, 0);
        LocalDateTime secondImpressionAt = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime applyAt = LocalDateTime.of(2026, 5, 1, 10, 30);

        Skill backendSkill = skill(1L, "Python", "Backend");
        Profile executor = profile(UUID.randomUUID(), "executor@test.local", Roles.EXECUTOR, "Минск", List.of(backendSkill));
        Profile customer = profile(UUID.randomUUID(), "customer@test.local", Roles.CUSTOMER, "Минск", List.of());
        Task task = Task.builder()
                .id(100L)
                .title("API task")
                .description("Build API")
                .city("Минск")
                .status(TaskStatus.OPEN)
                .createdAt(taskCreatedAt)
                .author(customer)
                .requiredSkills(List.of(backendSkill))
                .build();
        TaskApplication application = TaskApplication.builder()
                .id(500L)
                .task(task)
                .executor(executor)
                .status(ApplicationStatus.APPROVED)
                .createdAt(applyAt)
                .build();

        RecommendationImpression firstImpression = impression(1L, executor.getId(), task.getId(), firstImpressionAt);
        RecommendationImpression secondImpression = impression(2L, executor.getId(), task.getId(), secondImpressionAt);
        TaskViewEvent viewEvent = TaskViewEvent.builder()
                .id(10L)
                .viewerId(executor.getId())
                .taskId(task.getId())
                .viewedAt(secondImpressionAt.plusMinutes(5))
                .source(TaskViewSource.RECOMMENDATION)
                .build();

        when(recommendationImpressionRepository.findAllByOrderByRecommendedAtAscIdAsc())
                .thenReturn(List.of(firstImpression, secondImpression));
        when(recommendationImpressionRepository.findAll()).thenReturn(List.of(firstImpression, secondImpression));
        when(taskApplicationRepository.findAll()).thenReturn(List.of(application));
        when(taskViewEventRepository.findAll()).thenReturn(List.of(viewEvent));
        when(profileRepository.findAll()).thenReturn(List.of(executor, customer));
        when(taskRepository.findAll()).thenReturn(List.of(task));

        List<RealTrainingRowDto> rows = exportService.buildRows();

        assertThat(rows).hasSize(1);
        RealTrainingRowDto row = rows.get(0);
        assertThat(row.getExecutorId()).isEqualTo(executor.getId().toString());
        assertThat(row.getTaskId()).isEqualTo(task.getId());
        assertThat(row.getViewLabel()).isEqualTo(1);
        assertThat(row.getApplyLabel()).isEqualTo(1);
        assertThat(row.getApproveLabel()).isEqualTo(1);
        assertThat(row.getMatchedSkillsCount()).isEqualTo(1);
        assertThat(row.getSkillsOverlapRatio()).isEqualTo(1.0);
        assertThat(row.getRequiredSkillCount()).isEqualTo(1);
        assertThat(row.getExecutorSkillCount()).isEqualTo(1);
        assertThat(row.getExactSkillCover()).isEqualTo(1.0);
        assertThat(row.getSkillGapCount()).isZero();

        Path output = tempDir.resolve("real_training_dataset.csv");
        TrainingDatasetExportResponse response = exportService.exportCsv(output.toString());
        String csv = Files.readString(output);

        assertThat(response.getRows()).isEqualTo(1);
        assertThat(response.getUniqueExecutorTaskPairs()).isEqualTo(1);
        assertThat(response.getPositiveApplyPairs()).isEqualTo(1);
        assertThat(response.getDuplicatePairs()).isZero();
        assertThat(csv.lines().findFirst()).contains("""
                executor_id,task_id,executor_city,task_city,view_label,matched_skills_count,skills_overlap_ratio,city_match,category_affinity,freshness_bonus,required_skill_count,executor_skill_count,exact_skill_cover,skill_gap_count,task_age_hours,baseline_score,activity_level,quality_score,apply_label,approve_label""".trim());
    }

    private RecommendationImpression impression(Long id, UUID executorId, Long taskId, LocalDateTime recommendedAt) {
        return RecommendationImpression.builder()
                .id(id)
                .executorId(executorId)
                .taskId(taskId)
                .position(id.intValue())
                .recommendedAt(recommendedAt)
                .mode("ml")
                .modelVersion("apply-logreg-v1")
                .score(0.9)
                .scoreType("apply_probability")
                .build();
    }

    private Profile profile(UUID id, String email, Roles role, String city, List<Skill> skills) {
        return Profile.builder()
                .id(id)
                .user(User.builder()
                        .id(Math.abs((long) email.hashCode()))
                        .email(email)
                        .username(email)
                        .password("hash")
                        .createdAt(LocalDateTime.now())
                        .build())
                .role(role)
                .city(city)
                .skills(skills)
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
