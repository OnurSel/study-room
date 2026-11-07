package com.studyroom.task;

import com.studyroom.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * REST interface for the real-time shared task list.
 *
 * <p>The task service publishes mutations to the room's WebSocket topic.
 * The endpoints require an authenticated anonymous identity. Before opening
 * these routes in SecurityConfig, enforce room membership on each request;
 * possession of an anonymous JWT alone must not grant access to every room.</p>
 */
@RestController
@RequestMapping("/api/v1/rooms/{code}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<Task> list(@PathVariable String code,
                           @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return execute(() -> taskService.listTasks(code));
    }

    @PostMapping
    public ResponseEntity<Task> create(@PathVariable String code,
                                       @Valid @RequestBody CreateTaskRequest request,
                                       @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        JwtService.AnonymousIdentity user = requireIdentity(identity);
        Task task = execute(() -> taskService.addTask(code, request.title(), user.participantId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PatchMapping("/{taskId}/completion")
    public Task setCompletion(@PathVariable String code,
                              @PathVariable UUID taskId,
                              @Valid @RequestBody CompletionRequest request,
                              @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return execute(() -> taskService.setCompleted(
                code, taskId, request.completed(), request.expectedVersion()));
    }

    @PatchMapping("/{taskId}/title")
    public Task rename(@PathVariable String code,
                       @PathVariable UUID taskId,
                       @Valid @RequestBody RenameTaskRequest request,
                       @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        return execute(() -> taskService.renameTask(
                code, taskId, request.title(), request.expectedVersion()));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable String code,
                                       @PathVariable UUID taskId,
                                       @RequestParam @PositiveOrZero long expectedVersion,
                                       @AuthenticationPrincipal JwtService.AnonymousIdentity identity) {
        requireIdentity(identity);
        execute(() -> {
            taskService.removeTask(code, taskId, expectedVersion);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private static JwtService.AnonymousIdentity requireIdentity(JwtService.AnonymousIdentity identity) {
        if (identity == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return identity;
    }

    private static <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (TaskService.TaskConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record CreateTaskRequest(@NotBlank @Size(max = Task.MAX_TITLE_LENGTH) String title) {}

    public record CompletionRequest(@NotNull Boolean completed,
                                    @PositiveOrZero long expectedVersion) {}

    public record RenameTaskRequest(@NotBlank @Size(max = Task.MAX_TITLE_LENGTH) String title,
                                    @PositiveOrZero long expectedVersion) {}
}
