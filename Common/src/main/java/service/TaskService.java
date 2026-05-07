package service;

import domain.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import repository.*;
import utils.HibernateUtils;
import utils.PasswordUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer — Iteration 1 use cases:
 *   UC-1  Login
 *   UC-2  Create Task
 *   UC-3  Modify Task (unassigned only)
 *   UC-4  Delete Task
 *   UC-6  Self-Assign Task (concurrent-safe)
 *   UC-8  View Own Task Dashboard
 *   UC-12 Logout
 *   UC-13 View Task List
 */
public class TaskService {

    private static final Logger logger = Logger.getLogger(TaskService.class.getName());

    private final RepoUser repoUser;
    private final RepoTask repoTask;
    private final RepoAssignment repoAssignment;
    private final RepoLog repoLog;

    public TaskService(RepoUser repoUser, RepoTask repoTask,
                       RepoAssignment repoAssignment, RepoLog repoLog) {
        this.repoUser = repoUser;
        this.repoTask = repoTask;
        this.repoAssignment = repoAssignment;
        this.repoLog = repoLog;
    }

    // ─────────────────────────────────────────────
    // UC-1: Login
    // ─────────────────────────────────────────────

    public User login(String username, String password) throws ServiceException {
        try {
            String hashed = PasswordUtil.hashPassword(password);
            User user = repoUser.findByUsernameAndPassword(username, hashed);
            if (user == null) throw new ServiceException("Invalid username or password.");
            logger.info("User logged in: " + user);
            return user;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error during login", e);
            throw new ServiceException("Database connection error. Please try again later.");
        }
    }

    // ─────────────────────────────────────────────
    // UC-2: Create Task (Boss)
    // ─────────────────────────────────────────────

    public Task createTask(String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        validateTaskFields(title, description, deadline, priority);
        try {
            Task task = new Task(title.trim(), description.trim(), deadline, priority);
            repoTask.save(task);
            logger.info("Task created: " + task.getTitle());
            return task;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error creating task", e);
            throw new ServiceException("Database error. Task was not saved.");
        }
    }

    // ─────────────────────────────────────────────
    // UC-3: Modify Task (Boss, unassigned only)
    // ─────────────────────────────────────────────

    public void editTask(Long taskId, String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        Task task = getTaskOrThrow(taskId);
        if (task.getStatus() == Status.COMPLETE)
            throw new ServiceException("Task is already completed and cannot be modified.");
        if (task.getStatus() == Status.ASSIGNED)
            throw new ServiceException("Task is currently assigned to a worker and cannot be modified.");

        validateTaskFields(title, description, deadline, priority);
        try {
            task.setTitle(title.trim());
            task.setDescription(description.trim());
            task.setDeadline(deadline);
            task.setPriority(priority);
            repoTask.update(task);
            logger.info("Task updated: id=" + taskId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error updating task id=" + taskId, e);
            throw new ServiceException("Database error. Task was not updated.");
        }
    }

    // ─────────────────────────────────────────────
    // UC-4: Delete Task (Boss)
    // ─────────────────────────────────────────────

    /**
     * Deletes a task and removes any existing assignment.
     * Returns the userId of the displaced worker (if any) so the caller
     * can send a notification — satisfies NFR-5.
     */
    public Long deleteTask(Long taskId) throws ServiceException {
        Task task = getTaskOrThrow(taskId);
        if (task.getStatus() == Status.COMPLETE)
            throw new ServiceException("Task is already completed and cannot be deleted.");

        try {
            Optional<Assignment> existing = repoAssignment.findByTaskId(taskId);
            Long affectedUserId = null;
            if (existing.isPresent()) {
                affectedUserId = existing.get().getUserId();
                repoAssignment.deleteByTaskId(taskId);
            }
            repoTask.delete(taskId);
            logger.info("Task deleted: id=" + taskId);
            return affectedUserId;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error deleting task id=" + taskId, e);
            throw new ServiceException("Database error. Task was not deleted.");
        }
    }

    // ─────────────────────────────────────────────
    // UC-6: Self-Assign Task (Worker, concurrent-safe)
    // ─────────────────────────────────────────────

    /**
     * Atomically assigns an AVAILABLE task to a worker using a single
     * Hibernate session + transaction with a re-read inside the lock.
     * NFR-4: first requester wins; second gets a ServiceException.
     */
    public synchronized Assignment selfAssignTask(Long workerId, Long taskId) throws ServiceException {
        Transaction tx = null;
        try (Session session = HibernateUtils.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            // Re-read inside the transaction to get the true current state
            Task task = session.get(Task.class, taskId);
            if (task == null) throw new ServiceException("Task no longer exists.");
            if (task.getStatus() != Status.AVAILABLE)
                throw new ServiceException("Task is no longer available.");

            // Atomically flip status
            repoTask.updateStatus(taskId, Status.ASSIGNED, session);

            // Create assignment record
            Assignment assignment = new Assignment(workerId, taskId, LocalDate.now(), Status.ASSIGNED);
            repoAssignment.save(assignment, session);

            tx.commit();
            logger.info("Worker " + workerId + " self-assigned task " + taskId);
            return assignment;

        } catch (ServiceException e) {
            if (tx != null && tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            logger.log(Level.SEVERE, "DB error during selfAssignTask", e);
            throw new ServiceException("Database error while assigning task.");
        }
    }

    // ─────────────────────────────────────────────
    // UC-8: Worker Dashboard
    // ─────────────────────────────────────────────

    public List<Task> getWorkerDashboard(Long workerId) throws ServiceException {
        try {
            return repoAssignment.findByUserId(workerId).stream()
                    .filter(a -> a.getStatus() == Status.ASSIGNED)
                    .map(a -> repoTask.findById(a.getTaskId()))
                    .filter(t -> t != null)
                    .toList();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error loading dashboard for worker " + workerId, e);
            throw new ServiceException("Database connection error while loading dashboard.");
        }
    }

    // ─────────────────────────────────────────────
    // UC-12: Logout
    // ─────────────────────────────────────────────

    public void logout(Long userId) {
        logger.info("User logged out: id=" + userId);
        // Server layer removes the client from its active sessions map
    }

    // ─────────────────────────────────────────────
    // UC-13: View Task List (Boss) + helper for worker browse
    // ─────────────────────────────────────────────

    public List<Task> getAllTasks() throws ServiceException {
        try {
            return repoTask.getAll();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error fetching all tasks", e);
            throw new ServiceException("Database connection error. Could not retrieve tasks.");
        }
    }

    public List<Task> getAvailableTasks() throws ServiceException {
        try {
            return repoTask.findByStatus(Status.AVAILABLE);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error fetching available tasks", e);
            throw new ServiceException("Database connection error. Could not retrieve tasks.");
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private Task getTaskOrThrow(Long taskId) throws ServiceException {
        Task task = repoTask.findById(taskId);
        if (task == null) throw new ServiceException("Task not found.");
        return task;
    }

    private void validateTaskFields(String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        if (title == null || title.isBlank()) throw new ServiceException("Title is required.");
        if (description == null || description.isBlank()) throw new ServiceException("Description is required.");
        if (deadline == null) throw new ServiceException("Deadline is required.");
        if (deadline.isBefore(LocalDate.now())) throw new ServiceException("Deadline cannot be in the past.");
        if (priority == null) throw new ServiceException("Priority is required.");
    }
}