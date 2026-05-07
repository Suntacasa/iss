package repository;

import domain.Role;
import domain.User;
import org.hibernate.Session;

import java.util.List;

public class RepoUser extends AbstractRepository<Long, User> {

    public RepoUser() {
        super(User.class);
    }

    public User findByUsername(String username) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .uniqueResult();
        } catch (Exception e) {
            logger.severe("Error finding user by username=" + username);
            throw e;
        }
    }

    public User findByUsernameAndPassword(String username, String password) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM User u WHERE u.username = :username AND u.password = :password", User.class)
                    .setParameter("username", username)
                    .setParameter("password", password)
                    .uniqueResult();
        } catch (Exception e) {
            logger.severe("Error finding user by credentials");
            throw e;
        }
    }

    public List<User> findByRole(Role role) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM User u WHERE u.role = :role", User.class)
                    .setParameter("role", role)
                    .list();
        } catch (Exception e) {
            logger.severe("Error finding users by role=" + role);
            throw e;
        }
    }
}