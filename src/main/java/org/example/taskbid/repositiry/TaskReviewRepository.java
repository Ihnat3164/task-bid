package org.example.taskbid.repositiry;

import org.example.taskbid.entity.TaskReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskReviewRepository extends JpaRepository<TaskReview, Long> {
    boolean existsByTask_Id(Long taskId);

    Optional<TaskReview> findByTask_Id(Long taskId);

    long countByExecutor_Id(UUID executorId);

    @Query("select avg(r.rating) from TaskReview r where r.executor.id = :executorId")
    Double averageRatingByExecutorId(@Param("executorId") UUID executorId);
}
