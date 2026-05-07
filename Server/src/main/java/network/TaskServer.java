package network;

import service.ITaskService;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskServer {

    private static final Logger logger = Logger.getLogger(TaskServer.class.getName());

    private final ITaskService service;
    private final int port;
    private volatile boolean running = true;

    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public TaskServer(ITaskService service, int port) {
        this.service = service;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Server started on port " + port);
            System.out.println("Server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: " + clientSocket.getInetAddress());
                ClientWorker worker = new ClientWorker(service, clientSocket);
                threadPool.execute(worker);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Server error", e);
        } finally {
            threadPool.shutdown();
        }
    }

    public void stop() {
        running = false;
        threadPool.shutdown();
    }
}