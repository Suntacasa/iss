package repository;

import domain.Log;
import org.hibernate.Session;

import java.util.List;

public class RepoLog extends AbstractRepository<Long, Log> {

    public RepoLog() {
        super(Log.class);
    }

    public List<Log> findByWorkerId(Long workerId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Log l WHERE l.workerId = :workerId", Log.class)
                    .setParameter("workerId", workerId)
                    .list();
        } catch (Exception e) {
            logger.severe("Error finding logs for workerId=" + workerId);
            throw e;
        }
    }
}