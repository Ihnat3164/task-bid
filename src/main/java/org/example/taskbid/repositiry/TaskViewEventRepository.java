package org.example.taskbid.repositiry;

import org.example.taskbid.entity.TaskViewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskViewEventRepository extends JpaRepository<TaskViewEvent, Long> {
}
