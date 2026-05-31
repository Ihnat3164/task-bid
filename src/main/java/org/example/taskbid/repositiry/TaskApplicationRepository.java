package org.example.taskbid.repositiry;

import org.example.taskbid.dto.MyApplicationDto;
import org.example.taskbid.dto.TaskApplicationsCountDto;
import org.example.taskbid.entity.TaskApplication;
import org.example.taskbid.entity.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskApplicationRepository extends JpaRepository<TaskApplication, Long> {

    boolean existsByTask_IdAndExecutor_Id(Long taskId, UUID executorId);

    List<TaskApplication> findAllByTask_IdOrderByCreatedAtDesc(Long taskId);

    Optional<TaskApplication> findByIdAndTask_Id(Long id, Long taskId);

    @Query("""
        select new org.example.taskbid.dto.TaskApplicationsCountDto(a.task.id, count(a.id))
        from TaskApplication a
        where a.task.author.id = :authorProfileId
        group by a.task.id
    """)
    List<TaskApplicationsCountDto> countByAuthorTasks(UUID authorProfileId);

    List<TaskApplication> findAllByTask_Id(Long taskId);

    @Modifying
    @Query("delete from TaskApplication ta where ta.task.id = :taskId")
    void deleteAllByTaskId(Long taskId);

    @Query("""
        select new org.example.taskbid.dto.MyApplicationDto(
            ta.id,
            t.id,
            t.title,
            t.city,
            ta.status,
            ta.createdAt
        )
        from TaskApplication ta
        join ta.task t
        where ta.executor.id = :executorId
        order by ta.createdAt desc
    """)
    List<MyApplicationDto> findMyApplications(UUID executorId);

    @Modifying
    @Query("""
        update TaskApplication ta
        set ta.status = :status
        where ta.task.id = :taskId
          and ta.id <> :idToKeep
          and ta.status = org.example.taskbid.entity.enums.ApplicationStatus.PENDING
    """)
    void updatePendingStatusesByTaskIdAndIdNot(Long taskId, Long idToKeep, ApplicationStatus status);

    Optional<TaskApplication> findByTask_IdAndExecutor_Id(Long taskId, UUID executorId);
}
