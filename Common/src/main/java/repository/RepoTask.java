package repository;

import domain.Priority;
import domain.Status;
import domain.Task;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class RepoTask extends AbstractRepository<Long, Task> {

    public RepoTask() {
        super(Task.class);
    }

    public List<Task> findByStatus(Status status) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Task t WHERE t.status = :status", Task.class)
                    .setParameter("status", status)
                    .list();
        } catch (Exception e) {
            logger.severe("Error finding tasks by status=" + status);
            throw e;
        }
    }

    public List<Task> findByPriority(Priority priority) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Task t WHERE t.priority = :priority", Task.class)
                    .setParameter("priority", priority)
                    .list();
        } catch (Exception e) {
            logger.severe("Error finding tasks by priority=" + priority);
            throw e;
        }
    }

    public List<Task> findByStatusAndPriority(Status status, Priority priority) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Task t WHERE t.status = :status AND t.priority = :priority", Task.class)
                    .setParameter("status", status)
                    .setParameter("priority", priority)
                    .list();
        } catch (Exception e) {
            logger.severe("Error finding tasks by status=" + status + " and priority=" + priority);
            throw e;
        }
    }

    /**
     * Atomically updates a task's status within an already-open session/transaction.
     * Used by TaskService.selfAssignTask() for concurrency-safe assignment.
     */
    public void updateStatus(Long taskId, Status newStatus, Session session) {
        session.createMutationQuery(
                        "UPDATE Task t SET t.status = :status WHERE t.id = :id")
                .setParameter("status", newStatus)
                .setParameter("id", taskId)
                .executeUpdate();
    }
}