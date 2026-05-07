package domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "assignments")
public class Assignment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Store FK as plain Long — avoids lazy-loading headaches in a non-Spring setup
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true) // one assignment per task at a time
    private Long taskId;

    @Column(nullable = false)
    private LocalDate assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status; // ASSIGNED or COMPLETE

    public Assignment() {}

    public Assignment(Long userId, Long taskId, LocalDate assignedAt, Status status) {
        this.userId = userId;
        this.taskId = taskId;
        this.assignedAt = assignedAt;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public LocalDate getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDate assignedAt) { this.assignedAt = assignedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return "Assignment{id=" + id + ", userId=" + userId + ", taskId=" + taskId + ", status=" + status + '}';
    }
}