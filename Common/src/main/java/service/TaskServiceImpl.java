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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
public class TaskServiceImpl implements ITaskService {

    private static final Logger logger = Logger.getLogger(TaskServiceImpl.class.getName());

    private final RepoUser repoUser;
    private final RepoTask repoTask;
    private final RepoAssignment repoAssignment;
    private final RepoLog repoLog;

    // Thread-safe list of connected client workers
    private final List<ITaskObserver> observers = new CopyOnWriteArrayList<>();

    public TaskServiceImpl(RepoUser repoUser, RepoTask repoTask,
                           RepoAssignment repoAssignment, RepoLog repoLog) {
        this.repoUser = repoUser;
        this.repoTask = repoTask;
        this.repoAssignment = repoAssignment;
        this.repoLog = repoLog;
    }

    // ─── Observer management ───────────────────────────────────────────────

    @Override
    public void addObserver(ITaskObserver observer) {
        observers.add(observer);
        logger.info("Observer added. Total connected: " + observers.size());
    }

    @Override
    public void removeObserver(ITaskObserver observer) {
        observers.remove(observer);
        logger.info("Observer removed. Total connected: " + observers.size());
    }

    private void notifyObservers(List<Task> tasks) {
        for (ITaskObserver obs : observers) {
            try {
                obs.tasksUpdated(tasks);
            } catch (ServiceException e) {
                // Client disconnected mid-notify — remove it silently
                observers.remove(obs);
                logger.warning("Removed unresponsive observer during notify");
            }
        }
    }

    // ─── UC-1: Login / Logout ──────────────────────────────────────────────

    @Override
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

    @Override
    public void logout(User user) throws ServiceException {
        logger.info("User logged out: " + user);
        // Observer is removed by ClientWorker after this call
    }

    // ─── UC-2: Create Task ─────────────────────────────────────────────────

    @Override
    public Task createTask(String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        validateTaskFields(title, description, deadline, priority);
        try {
            Task task = new Task(title.trim(), description.trim(), deadline, priority);
            repoTask.save(task);
            notifyObservers(repoTask.getAll());
            logger.info("Task created: " + task.getTitle());
            return task;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error creating task", e);
            throw new ServiceException("Database error. Task was not saved.");
        }
    }

    // ─── UC-3: Edit Task ───────────────────────────────────────────────────

    @Override
    public void editTask(Long taskId, String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        Task task = getTaskOrThrow(taskId);
        if (task.getStatus() == Status.COMPLETE)
            throw new ServiceException("Task is already completed and cannot be modified.");
        if (task.getStatus() == Status.ASSIGNED)
            throw new ServiceException("Task is assigned to a worker and cannot be modified.");

        validateTaskFields(title, description, deadline, priority);
        try {
            task.setTitle(title.trim());
            task.setDescription(description.trim());
            task.setDeadline(deadline);
            task.setPriority(priority);
            repoTask.update(task);
            notifyObservers(repoTask.getAll());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error updating task id=" + taskId, e);
            throw new ServiceException("Database error. Task was not updated.");
        }
    }

    // ─── UC-4: Delete Task ─────────────────────────────────────────────────

    @Override
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
            notifyObservers(repoTask.getAll());
            logger.info("Task deleted: id=" + taskId);
            return affectedUserId;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error deleting task id=" + taskId, e);
            throw new ServiceException("Database error. Task was not deleted.");
        }
    }

    // ─── UC-6: Self-Assign Task ────────────────────────────────────────────

    @Override
    public synchronized void selfAssignTask(Long workerId, Long taskId) throws ServiceException {
        Transaction tx = null;
        try (Session session = HibernateUtils.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Task task = session.get(Task.class, taskId);
            if (task == null) throw new ServiceException("Task no longer exists.");
            if (task.getStatus() != Status.AVAILABLE)
                throw new ServiceException("Task is no longer available.");

            repoTask.updateStatus(taskId, Status.ASSIGNED, session);

            Assignment assignment = new Assignment(workerId, taskId, LocalDate.now(), Status.ASSIGNED);
            repoAssignment.save(assignment, session);

            tx.commit();
            logger.info("Worker " + workerId + " self-assigned task " + taskId);

            notifyObservers(repoTask.getAll());

        } catch (ServiceException e) {
            if (tx != null && tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            logger.log(Level.SEVERE, "DB error during selfAssignTask", e);
            throw new ServiceException("Database error while assigning task.");
        }
    }

    // ─── UC-8: Worker Dashboard ────────────────────────────────────────────

    @Override
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

    // ─── UC-9: Filter available tasks by priority ──────────────────────────

    @Override
    public List<Task> getAvailableTasksByPriority(Priority priority) throws ServiceException {
        try {
            return repoTask.findByStatusAndPriority(Status.AVAILABLE, priority);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error filtering tasks", e);
            throw new ServiceException("Database error while filtering tasks.");
        }
    }

    // ─── UC-5: Boss assigns task to a specific worker ──────────────────────

    @Override
    public synchronized void assignTaskToWorker(Long taskId, Long workerId) throws ServiceException {
        Transaction tx = null;
        try (Session session = HibernateUtils.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Task task = session.get(Task.class, taskId);
            if (task == null) throw new ServiceException("Task not found.");
            if (task.getStatus() != Status.AVAILABLE)
                throw new ServiceException("Task is no longer available — it may have just been taken.");

            User worker = session.get(User.class, workerId);
            if (worker == null) throw new ServiceException("Worker not found.");

            repoTask.updateStatus(taskId, Status.ASSIGNED, session);

            Assignment assignment = new Assignment(workerId, taskId, LocalDate.now(), Status.ASSIGNED);
            repoAssignment.save(assignment, session);

            tx.commit();
            logger.info("Boss assigned task " + taskId + " to worker " + workerId);

            notifyObservers(repoTask.getAll());

        } catch (ServiceException e) {
            if (tx != null && tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            logger.log(Level.SEVERE, "DB error during assignTaskToWorker", e);
            throw new ServiceException("Database error while assigning task.");
        }
    }

    // ─── UC-7: Worker marks task as complete ──────────────────────────────

    @Override
    public synchronized void markTaskComplete(Long workerId, Long taskId) throws ServiceException {
        Transaction tx = null;
        try (Session session = HibernateUtils.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Task task = session.get(Task.class, taskId);
            if (task == null) throw new ServiceException("Task no longer exists.");
            if (task.getStatus() != Status.ASSIGNED)
                throw new ServiceException("Task is not currently assigned.");

            // Update task status to COMPLETE
            repoTask.updateStatus(taskId, Status.COMPLETE, session);

            // Update assignment status
            session.createMutationQuery(
                            "UPDATE Assignment a SET a.status = :status WHERE a.taskId = :taskId")
                    .setParameter("status", Status.COMPLETE)
                    .setParameter("taskId", taskId)
                    .executeUpdate();

            // Write log entry — flag as late if past deadline
            boolean isLate = task.getDeadline().isBefore(LocalDate.now());
            Log log = new Log(taskId, workerId, LocalDate.now(), isLate);
            session.persist(log);

            tx.commit();
            logger.info("Worker " + workerId + " completed task " + taskId + (isLate ? " (LATE)" : ""));

            notifyObservers(repoTask.getAll());

        } catch (ServiceException e) {
            if (tx != null && tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            logger.log(Level.SEVERE, "DB error during markTaskComplete", e);
            throw new ServiceException("Database error while completing task.");
        }
    }

    // ─── UC-11: Worker drops task ──────────────────────────────────────────

    @Override
    public synchronized void dropTask(Long workerId, Long taskId) throws ServiceException {
        Transaction tx = null;
        try (Session session = HibernateUtils.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            Task task = session.get(Task.class, taskId);
            if (task == null) throw new ServiceException("Task no longer exists.");
            if (task.getStatus() != Status.ASSIGNED)
                throw new ServiceException("Task is not currently assigned.");

            // Revert task to available
            repoTask.updateStatus(taskId, Status.AVAILABLE, session);

            // Remove the assignment record
            session.createMutationQuery(
                            "DELETE FROM Assignment a WHERE a.taskId = :taskId AND a.userId = :userId")
                    .setParameter("taskId", taskId)
                    .setParameter("userId", workerId)
                    .executeUpdate();

            tx.commit();
            logger.info("Worker " + workerId + " dropped task " + taskId);

            notifyObservers(repoTask.getAll());

        } catch (ServiceException e) {
            if (tx != null && tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) tx.rollback();
            logger.log(Level.SEVERE, "DB error during dropTask", e);
            throw new ServiceException("Database error while dropping task.");
        }
    }

    // ─── UC-10: Boss views worker logs ────────────────────────────────────

    @Override
    public List<Log> getWorkerLogs(Long workerId) throws ServiceException {
        try {
            User worker = repoUser.findById(workerId);
            if (worker == null) throw new ServiceException("Worker not found.");
            return repoLog.findByWorkerId(workerId);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error fetching logs for worker " + workerId, e);
            throw new ServiceException("Database error while fetching worker logs.");
        }
    }

    // ─── UC-5 helper: get all workers ─────────────────────────────────────

    @Override
    public List<User> getAllWorkers() throws ServiceException {
        try {
            return repoUser.findByRole(Role.WORKER);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error fetching workers", e);
            throw new ServiceException("Database error while fetching workers.");
        }
    }

    // ─── UC-13: Task Lists ─────────────────────────────────────────────────

    @Override
    public List<Task> getAllTasks() throws ServiceException {
        try {
            return repoTask.getAll();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error fetching all tasks", e);
            throw new ServiceException("Database connection error. Could not retrieve tasks.");
        }
    }

    @Override
    public List<Task> getAvailableTasks() throws ServiceException {
        try {
            return repoTask.findByStatus(Status.AVAILABLE);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DB error fetching available tasks", e);
            throw new ServiceException("Database connection error. Could not retrieve tasks.");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

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