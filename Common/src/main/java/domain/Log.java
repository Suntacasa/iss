package domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "logs")
public class Log implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Long workerId;

    @Column(nullable = false)
    private LocalDate completedAt;

    @Column(nullable = false)
    private boolean isLate;

    public Log() {}

    public Log(Long taskId, Long workerId, LocalDate completedAt, boolean isLate) {
        this.taskId = taskId;
        this.workerId = workerId;
        this.completedAt = completedAt;
        this.isLate = isLate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getWorkerId() { return workerId; }
    public void setWorkerId(Long workerId) { this.workerId = workerId; }

    public LocalDate getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDate completedAt) { this.completedAt = completedAt; }

    public boolean isLate() { return isLate; }
    public void setLate(boolean late) { isLate = late; }

    @Override
    public String toString() {
        return "Log{id=" + id + ", taskId=" + taskId + ", workerId=" + workerId + ", completedAt=" + completedAt + ", isLate=" + isLate + '}';
    }
}