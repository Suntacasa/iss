package network;

import java.io.Serializable;

public class AssignTaskDTO implements Serializable {
    private Long taskId;
    private Long workerId;

    public AssignTaskDTO(Long taskId, Long workerId) {
        this.taskId = taskId;
        this.workerId = workerId;
    }

    public Long getTaskId() { return taskId; }
    public Long getWorkerId() { return workerId; }
}