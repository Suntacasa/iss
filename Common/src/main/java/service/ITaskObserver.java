package service;

import domain.Task;
import java.util.List;

public interface ITaskObserver {
    /**
     * Called by the server on every connected client when the task list changes
     * (e.g. a task was created, deleted, or its status changed).
     */
    void tasksUpdated(List<Task> tasks) throws ServiceException;
}