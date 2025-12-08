package org.example.taskbid.repositiry;

import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT t FROM Task t WHERE t.author.user.email = :email")
    List<Task> findAllByUserEmail(@Param("email") String email);

    List<Task> findAllByAuthor(Profile profile);

    Task findTaskById(Long id);
}
