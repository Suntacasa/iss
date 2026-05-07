package service;

import domain.Log;
import domain.Priority;
import domain.Task;
import domain.User;

import java.time.LocalDate;
import java.util.List;

public interface ITaskService {

    // UC-1: Login / Logout
    User login(String username, String password) throws ServiceException;
    void logout(User user) throws ServiceException;

    // UC-2: Create Task (Boss)
    Task createTask(String title, String description, LocalDate deadline, Priority priority) throws ServiceException;

    // UC-3: Edit Task (Boss, unassigned only)
    void editTask(Long taskId, String title, String description, LocalDate deadline, Priority priority) throws ServiceException;

    // UC-4: Delete Task (Boss) — returns userId of displaced worker, or null
    Long deleteTask(Long taskId) throws ServiceException;

    // UC-6: Self-assign Task (Worker)
    void selfAssignTask(Long workerId, Long taskId) throws ServiceException;

    // UC-8: Worker dashboard
    List<Task> getWorkerDashboard(Long workerId) throws ServiceException;

    // UC-13: All tasks (Boss view)
    List<Task> getAllTasks() throws ServiceException;

    // UC-13 helper: available tasks only (Worker browse)
    List<Task> getAvailableTasks() throws ServiceException;

    // UC-9: Filter available tasks by priority
    List<Task> getAvailableTasksByPriority(Priority priority) throws ServiceException;

    // UC-5: Boss assigns task to a specific worker
    void assignTaskToWorker(Long taskId, Long workerId) throws ServiceException;

    // UC-7: Worker marks task as complete (writes Log entry)
    void markTaskComplete(Long workerId, Long taskId) throws ServiceException;

    // UC-11: Worker drops task back to available
    void dropTask(Long workerId, Long taskId) throws ServiceException;

    // UC-10: Boss views a worker's log history
    List<Log> getWorkerLogs(Long workerId) throws ServiceException;

    // UC-5 helper: get all workers for the assign dialog
    List<User> getAllWorkers() throws ServiceException;

    // Observer management
    void addObserver(ITaskObserver observer);
    void removeObserver(ITaskObserver observer);
}