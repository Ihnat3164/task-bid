package org.example.taskbid.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.component.JwtUtil;
import org.example.taskbid.dto.ExecutorRatingDto;
import org.example.taskbid.dto.TaskReviewRequest;
import org.example.taskbid.dto.TaskReviewResponse;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.TaskReview;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.TaskStatus;
import org.example.taskbid.exception.BusinessException;
import org.example.taskbid.exception.NotFoundException;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.TaskRepository;
import org.example.taskbid.repositiry.TaskReviewRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TaskReviewService {

    TaskReviewRepository taskReviewRepository;
    TaskRepository taskRepository;
    UserRepository userRepository;
    ProfileRepository profileRepository;
    JwtUtil jwtUtil;

    @Transactional
    public TaskReviewResponse createReview(Long taskId, TaskReviewRequest request, String token) {
        int rating = validatedRating(request);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Task not found"));
        Profile customer = resolveProfile(token);

        validateReviewAllowed(task, customer);

        TaskReview review = TaskReview.builder()
                .task(task)
                .customer(customer)
                .executor(task.getExecutor())
                .rating(rating)
                .comment(normalizeComment(request.getComment()))
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(taskReviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public boolean canCurrentUserReview(Task task, Profile viewer) {
        return viewer != null
                && task != null
                && task.getStatus() == TaskStatus.COMPLETED
                && task.getExecutor() != null
                && task.getAuthor() != null
                && task.getAuthor().getId().equals(viewer.getId())
                && !taskReviewRepository.existsByTask_Id(task.getId());
    }

    @Transactional(readOnly = true)
    public Optional<TaskReviewResponse> findTaskReview(Long taskId) {
        return taskReviewRepository.findByTask_Id(taskId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ExecutorRatingDto ratingForExecutor(Profile executor) {
        if (executor == null || executor.getId() == null) {
            return ExecutorRatingDto.builder()
                    .averageRating(null)
                    .reviewsCount(0L)
                    .build();
        }

        long count = taskReviewRepository.countByExecutor_Id(executor.getId());
        Double average = count == 0 ? null : taskReviewRepository.averageRatingByExecutorId(executor.getId());
        return ExecutorRatingDto.builder()
                .averageRating(average == null ? null : Math.round(average * 100.0) / 100.0)
                .reviewsCount(count)
                .build();
    }

    public TaskReviewResponse toResponse(TaskReview review) {
        return TaskReviewResponse.builder()
                .id(review.getId())
                .taskId(review.getTask().getId())
                .executorId(review.getExecutor().getId())
                .customerId(review.getCustomer().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private int validatedRating(TaskReviewRequest request) {
        if (request == null || request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException("INVALID_REVIEW_RATING", "Rating must be between 1 and 5");
        }
        return request.getRating();
    }

    private void validateReviewAllowed(Task task, Profile customer) {
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new BusinessException("TASK_NOT_COMPLETED", "Review can be created only for completed task");
        }
        if (task.getAuthor() == null || !task.getAuthor().getId().equals(customer.getId())) {
            throw new BusinessException("REVIEW_FORBIDDEN", "Only task author can review executor");
        }
        if (task.getExecutor() == null) {
            throw new BusinessException("TASK_EXECUTOR_MISSING", "Completed task has no assigned executor");
        }
        if (taskReviewRepository.existsByTask_Id(task.getId())) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "Review already exists for this task");
        }
    }

    private Profile resolveProfile(String token) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        return profileRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("PROFILE_NOT_FOUND", "Profile not found"));
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            return null;
        }
        return comment.trim();
    }
}
