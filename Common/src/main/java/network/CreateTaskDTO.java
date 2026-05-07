package network;

import domain.Priority;
import java.io.Serializable;
import java.time.LocalDate;

public class CreateTaskDTO implements Serializable {
    private String title;
    private String description;
    private LocalDate deadline;
    private Priority priority;

    public CreateTaskDTO(String title, String description, LocalDate deadline, Priority priority) {
        this.title = title;
        this.description = description;
        this.deadline = deadline;
        this.priority = priority;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDeadline() { return deadline; }
    public Priority getPriority() { return priority; }
}