package utils;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HibernateUtils {

    private static final Logger logger = Logger.getLogger(HibernateUtils.class.getName());
    private static SessionFactory sessionFactory;

    private HibernateUtils() {}

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            try {
                sessionFactory = new Configuration()
                        .configure("hibernate.cfg.xml")
                        .buildSessionFactory();
                logger.info("SessionFactory created successfully");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to create SessionFactory", e);
                throw new RuntimeException("Database connection error — could not initialise Hibernate.", e);
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
            logger.info("SessionFactory closed");
        }
    }
}