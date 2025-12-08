package org.example.taskbid.controller;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.example.taskbid.dto.CreateTaskRequest;
import org.example.taskbid.dto.TaskDto;
import org.example.taskbid.dto.TasksDto;
import org.example.taskbid.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TaskController {

    TaskService taskService;

    @PostMapping("/tasks")
    public ResponseEntity<Void> createTask(HttpServletRequest req,
                                              @RequestBody CreateTaskRequest createTaskRequest) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);
        taskService.createTask(createTaskRequest, token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<TasksDto>> getMyTasks(HttpServletRequest req) {

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        return ResponseEntity.ok(taskService.getTasksByUser(token));
    }

    @GetMapping("/task")
    public ResponseEntity<TaskDto> getTask(@RequestParam Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }
}
