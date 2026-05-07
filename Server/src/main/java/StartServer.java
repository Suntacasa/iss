import network.TaskServer;
import repository.*;
import service.TaskServiceImpl;
import utils.HibernateUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

public class StartServer {

    private static final Logger logger = Logger.getLogger(StartServer.class.getName());
    private static final int PORT = 5557;

    public static void main(String[] args) {
        try {
            HibernateUtils.getSessionFactory();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Cannot connect to database. Server will not start.", e);
            System.out.println("Cannot connect to database: " + e.getMessage());
            return;
        }

        RepoUser repoUser = new RepoUser();
        RepoTask repoTask = new RepoTask();
        RepoAssignment repoAssignment = new RepoAssignment();
        RepoLog repoLog = new RepoLog();

        TaskServiceImpl service = new TaskServiceImpl(repoUser, repoTask, repoAssignment, repoLog);

        TaskServer server = new TaskServer(service, PORT);

        logger.info("Starting server on port " + PORT);
        server.start();
    }
}