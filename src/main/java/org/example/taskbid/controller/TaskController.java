package org.example.taskbid.controller;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
import org.example.taskbid.dto.*;
import org.example.taskbid.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@Slf4j
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
    public ResponseEntity<TaskDto> getTask(
            @RequestParam Long id,
            HttpServletRequest req
    ) {
        String authHeader = req.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        return ResponseEntity.ok(taskService.getTask(id, token));
    }


    @GetMapping("/recommendations")
    public ResponseEntity<List<TaskDto>> getRecommendations(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        String token = authHeader.substring(7);
        return ResponseEntity.ok(taskService.recommendTasks(token));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(HttpServletRequest req, @PathVariable Long id) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        taskService.deleteTask(id, token);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/tasks/all")
    public ResponseEntity<List<TaskDto>> getAllOpenTasks(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        return ResponseEntity.ok(taskService.getAllOpenTasksExceptMine(token));
    }

    @PostMapping("/tasks/{id}/apply")
    public ResponseEntity<Void> applyToTask(
            HttpServletRequest req,
            @PathVariable Long id,
            @RequestBody String price
    ) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        String token = authHeader.substring(7);

        taskService.applyToTask(id, price, token);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/my/tasks/applications-count")
    public ResponseEntity<List<TaskApplicationsCountDto>> myTasksApplicationsCount(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        log.info("how much");

        return ResponseEntity.ok(taskService.getMyTasksApplicationsCounts(token));
    }

    @PostMapping("/tasks/{taskId}/applications/{appId}/approve")
    public ResponseEntity<Void> approve(
            HttpServletRequest req,
            @PathVariable Long taskId,
            @PathVariable Long appId
    ) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        taskService.approveApplication(taskId, appId, token);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/my/applications")
    public ResponseEntity<List<MyApplicationDto>> myApplications(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        return ResponseEntity.ok(taskService.getMyApplications(token));
    }

    @PostMapping("/tasks/{taskId}/start-work")
    public ResponseEntity<Void> startWork(HttpServletRequest req, @PathVariable Long taskId) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        taskService.startWork(taskId, token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/finish-work")
    public ResponseEntity<Void> finishWork(HttpServletRequest req, @PathVariable Long taskId) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);

        taskService.finishWork(taskId, token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeTask(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String auth
    ) {
        String token = auth.substring(7);
        taskService.completeTask(taskId, token);
        return ResponseEntity.ok().build();
    }

}
