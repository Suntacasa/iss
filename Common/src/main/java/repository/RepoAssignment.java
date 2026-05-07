package repository;

import domain.Assignment;
import domain.Status;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Optional;

public class RepoAssignment extends AbstractRepository<Long, Assignment> {

    public RepoAssignment() {
        super(Assignment.class);
    }

    public List<Assignment> findByUserId(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Assignment a WHERE a.userId = :userId", Assignment.class)
                    .setParameter("userId", userId)
                    .list();
        } catch (Exception e) {
            logger.severe("Error finding assignments for userId=" + userId);
            throw e;
        }
    }

    public Optional<Assignment> findByTaskId(Long taskId) {
        try (Session session = sessionFactory.openSession()) {
            Assignment a = session.createQuery(
                            "FROM Assignment a WHERE a.taskId = :taskId", Assignment.class)
                    .setParameter("taskId", taskId)
                    .uniqueResult();
            return Optional.ofNullable(a);
        } catch (Exception e) {
            logger.severe("Error finding assignment for taskId=" + taskId);
            throw e;
        }
    }

    public void deleteByTaskId(Long taskId) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                            "DELETE FROM Assignment a WHERE a.taskId = :taskId")
                    .setParameter("taskId", taskId)
                    .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            logger.severe("Error deleting assignment for taskId=" + taskId);
            throw e;
        }
    }

    /**
     * Save inside a caller-managed session (used in selfAssignTask transaction).
     */
    public void save(Assignment assignment, Session session) {
        session.persist(assignment);
    }
}