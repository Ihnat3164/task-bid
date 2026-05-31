package org.example.taskbid.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Task;
import org.example.taskbid.entity.TaskViewEvent;
import org.example.taskbid.entity.enums.TaskViewSource;
import org.example.taskbid.repositiry.TaskViewEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TaskViewEventService {

    TaskViewEventRepository taskViewEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logView(Profile viewer, Task task, String source) {
        if (viewer == null || task == null) {
            return;
        }

        taskViewEventRepository.save(TaskViewEvent.builder()
                .viewerId(viewer.getId())
                .taskId(task.getId())
                .viewedAt(LocalDateTime.now())
                .source(TaskViewSource.fromNullable(source))
                .requestId(null)
                .build());
    }
}
