package org.example.taskbid.service;

import org.example.taskbid.component.JwtUtil;
import org.example.taskbid.dto.ExecutorRatingDto;
import org.example.taskbid.dto.TaskReviewRequest;
import org.example.taskbid.dto.TaskReviewResponse;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.TaskReview;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.Roles;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.exception.BusinessException;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.TaskReviewRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReviewServiceTest {

    @Mock
    TaskReviewRepository taskReviewRepository;
    @Mock
    TaskRepository taskRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ProfileRepository profileRepository;
    @Mock
    JwtUtil jwtUtil;

    @InjectMocks
    TaskReviewService taskReviewService;

    User customerUser;
    Profile customer;
    Profile otherCustomer;
    Profile executor;

    @BeforeEach
    void setUp() {
        customerUser = user("customer@test.local");
        customer = profile(UUID.randomUUID(), customerUser, Roles.CUSTOMER);
        otherCustomer = profile(UUID.randomUUID(), user("other@test.local"), Roles.CUSTOMER);
        executor = profile(UUID.randomUUID(), user("executor@test.local"), Roles.EXECUTOR);
    }

    @Test
    void createsReviewForCompletedTaskByAuthor() {
        Task task = task(10L, TaskStatus.COMPLETED, customer, executor);
        TaskReviewRequest request = request(5, "Хороший исполнитель");

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(jwtUtil.extractEmail("customer-token")).thenReturn(customerUser.getEmail());
        when(userRepository.findByEmail(customerUser.getEmail())).thenReturn(Optional.of(customerUser));
        when(profileRepository.findByUser(customerUser)).thenReturn(Optional.of(customer));
        when(taskReviewRepository.existsByTask_Id(10L)).thenReturn(false);
        when(taskReviewRepository.save(any())).thenAnswer(invocation -> {
            TaskReview review = invocation.getArgument(0);
            review.setId(100L);
            return review;
        });

        TaskReviewResponse response = taskReviewService.createReview(10L, request, "customer-token");

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTaskId()).isEqualTo(10L);
        assertThat(response.getExecutorId()).isEqualTo(executor.getId());
        assertThat(response.getCustomerId()).isEqualTo(customer.getId());
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Хороший исполнитель");

        ArgumentCaptor<TaskReview> captor = ArgumentCaptor.forClass(TaskReview.class);
        verify(taskReviewRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsReviewForUnfinishedTask() {
        Task task = task(11L, TaskStatus.IN_PROGRESS, customer, executor);

        when(taskRepository.findById(11L)).thenReturn(Optional.of(task));
        mockCustomerToken();

        assertThatThrownBy(() -> taskReviewService.createReview(11L, request(5, ""), "customer-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Review can be created only for completed task");

        verify(taskReviewRepository, never()).save(any());
    }

    @Test
    void rejectsReviewByNonAuthor() {
        Task task = task(12L, TaskStatus.COMPLETED, otherCustomer, executor);

        when(taskRepository.findById(12L)).thenReturn(Optional.of(task));
        mockCustomerToken();

        assertThatThrownBy(() -> taskReviewService.createReview(12L, request(5, ""), "customer-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only task author can review executor");

        verify(taskReviewRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateReviewForSameTask() {
        Task task = task(13L, TaskStatus.COMPLETED, customer, executor);

        when(taskRepository.findById(13L)).thenReturn(Optional.of(task));
        mockCustomerToken();
        when(taskReviewRepository.existsByTask_Id(13L)).thenReturn(true);

        assertThatThrownBy(() -> taskReviewService.createReview(13L, request(5, ""), "customer-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Review already exists for this task");

        verify(taskReviewRepository, never()).save(any());
    }

    @Test
    void calculatesExecutorAverageRatingAndReviewsCount() {
        when(taskReviewRepository.countByExecutor_Id(executor.getId())).thenReturn(3L);
        when(taskReviewRepository.averageRatingByExecutorId(executor.getId())).thenReturn(4.3333);

        ExecutorRatingDto rating = taskReviewService.ratingForExecutor(executor);

        assertThat(rating.getReviewsCount()).isEqualTo(3L);
        assertThat(rating.getAverageRating()).isEqualTo(4.33);
    }

    private void mockCustomerToken() {
        when(jwtUtil.extractEmail("customer-token")).thenReturn(customerUser.getEmail());
        when(userRepository.findByEmail(customerUser.getEmail())).thenReturn(Optional.of(customerUser));
        when(profileRepository.findByUser(customerUser)).thenReturn(Optional.of(customer));
    }

    private TaskReviewRequest request(Integer rating, String comment) {
        TaskReviewRequest request = new TaskReviewRequest();
        request.setRating(rating);
        request.setComment(comment);
        return request;
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

    private Profile profile(UUID id, User user, Roles role) {
        return Profile.builder()
                .id(id)
                .user(user)
                .role(role)
                .city("Минск")
                .skills(List.of())
                .build();
    }

    private Task task(Long id, TaskStatus status, Profile author, Profile executor) {
        return Task.builder()
                .id(id)
                .title("Task " + id)
                .description("Description")
                .city("Минск")
                .status(status)
                .createdAt(LocalDateTime.now().minusDays(1))
                .author(author)
                .executor(executor)
                .requiredSkills(List.of())
                .build();
    }
}
