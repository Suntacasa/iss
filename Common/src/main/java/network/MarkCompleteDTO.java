package network;

import java.io.Serializable;

public class MarkCompleteDTO implements Serializable {
    private Long workerId;
    private Long taskId;

    public MarkCompleteDTO(Long workerId, Long taskId) {
        this.workerId = workerId;
        this.taskId = taskId;
    }

    public Long getWorkerId() { return workerId; }
    public Long getTaskId() { return taskId; }
}