package network;

import domain.Priority;
import java.io.Serializable;
import java.time.LocalDate;

public class EditTaskDTO implements Serializable {
    private Long taskId;
    private String title;
    private String description;
    private LocalDate deadline;
    private Priority priority;

    public EditTaskDTO(Long taskId, String title, String description, LocalDate deadline, Priority priority) {
        this.taskId = taskId;
        this.title = title;
        this.description = description;
        this.deadline = deadline;
        this.priority = priority;
    }

    public Long getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDeadline() { return deadline; }
    public Priority getPriority() { return priority; }
}