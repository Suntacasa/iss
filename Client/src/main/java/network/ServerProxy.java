package network;

import domain.Log;
import domain.Priority;
import domain.Task;
import domain.User;
import javafx.application.Platform;
import networkDTO.*;
import service.ITaskObserver;
import service.ITaskService;
import service.ServiceException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerProxy implements ITaskService {

    private static final Logger logger = Logger.getLogger(ServerProxy.class.getName());

    private final String host;
    private final int port;

    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    private final BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

    // The local GUI controller that wants push notifications
    private ITaskObserver localObserver;
    private volatile boolean connected = false;

    public ServerProxy(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws ServiceException {
        try {
            socket = new Socket(host, port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            connected = true;
            startResponseReaderThread();
            logger.info("Connected to server " + host + ":" + port);
        } catch (IOException e) {
            throw new ServiceException("Cannot connect to server: " + e.getMessage(), e);
        }
    }

    // Background thread: reads all incoming responses/pushes from the server
    private void startResponseReaderThread() {
        Thread t = new Thread(() -> {
            while (connected) {
                try {
                    Response response = (Response) input.readObject();
                    logger.info("Response received: " + response.getType());

                    if (response.getType() == ResponseType.TASKS_UPDATED) {
                        // Push notification — route to observer on JavaFX thread
                        if (localObserver != null) {
                            List<Task> tasks = (List<Task>) response.getData();
                            Platform.runLater(() -> {
                                try {
                                    localObserver.tasksUpdated(tasks);
                                } catch (ServiceException e) {
                                    logger.log(Level.WARNING, "Error updating GUI from push", e);
                                }
                            });
                        }
                    } else {
                        // Regular request/response — put in queue for sendAndReceive()
                        responseQueue.put(response);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    logger.info("Connection closed: " + e.getMessage());
                    connected = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "ResponseReaderThread");
        t.setDaemon(true);
        t.start();
    }

    private Response sendAndReceive(Request request) throws ServiceException {
        try {
            synchronized (output) {
                output.writeObject(request);
                output.flush();
            }
            return responseQueue.take();
        } catch (IOException | InterruptedException e) {
            throw new ServiceException("Communication error with server: " + e.getMessage(), e);
        }
    }

    private void checkResponse(Response response) throws ServiceException {
        if (response.getType() == ResponseType.ERROR) {
            throw new ServiceException(response.getErrorMessage());
        }
    }

    // ─── ITaskService implementation ──────────────────────────────────────

    @Override
    public User login(String username, String password) throws ServiceException {
        // Send a dummy User just to carry the credentials (password NOT hashed — server hashes)
        User dummy = new User(username, password, null);
        Response response = sendAndReceive(new Request(RequestType.LOGIN, dummy));
        checkResponse(response);
        return (User) response.getData();
    }

    @Override
    public void logout(User user) throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.LOGOUT, user));
        checkResponse(response);
    }

    @Override
    public Task createTask(String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        CreateTaskDTO dto = new CreateTaskDTO(title, description, deadline, priority);
        Response response = sendAndReceive(new Request(RequestType.CREATE_TASK, dto));
        checkResponse(response);
        return (Task) response.getData();
    }

    @Override
    public void editTask(Long taskId, String title, String description, LocalDate deadline, Priority priority) throws ServiceException {
        EditTaskDTO dto = new EditTaskDTO(taskId, title, description, deadline, priority);
        Response response = sendAndReceive(new Request(RequestType.EDIT_TASK, dto));
        checkResponse(response);
    }

    @Override
    public Long deleteTask(Long taskId) throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.DELETE_TASK, taskId));
        checkResponse(response);
        return (Long) response.getData();
    }

    @Override
    public List<Task> getAllTasks() throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.GET_ALL_TASKS, null));
        checkResponse(response);
        return (List<Task>) response.getData();
    }

    @Override
    public List<Task> getAvailableTasks() throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.GET_AVAILABLE_TASKS, null));
        checkResponse(response);
        return (List<Task>) response.getData();
    }

    @Override
    public List<Task> getWorkerDashboard(Long workerId) throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.GET_WORKER_DASHBOARD, workerId));
        checkResponse(response);
        return (List<Task>) response.getData();
    }

    @Override
    public void selfAssignTask(Long workerId, Long taskId) throws ServiceException {
        SelfAssignDTO dto = new SelfAssignDTO(workerId, taskId);
        Response response = sendAndReceive(new Request(RequestType.SELF_ASSIGN_TASK, dto));
        checkResponse(response);
    }

    @Override
    public void assignTaskToWorker(Long taskId, Long workerId) throws ServiceException {
        AssignTaskDTO dto = new AssignTaskDTO(taskId, workerId);
        Response response = sendAndReceive(new Request(RequestType.ASSIGN_TASK_TO_WORKER, dto));
        checkResponse(response);
    }

    @Override
    public void markTaskComplete(Long workerId, Long taskId) throws ServiceException {
        MarkCompleteDTO dto = new MarkCompleteDTO(workerId, taskId);
        Response response = sendAndReceive(new Request(RequestType.MARK_TASK_COMPLETE, dto));
        checkResponse(response);
    }

    @Override
    public void dropTask(Long workerId, Long taskId) throws ServiceException {
        DropTaskDTO dto = new DropTaskDTO(workerId, taskId);
        Response response = sendAndReceive(new Request(RequestType.DROP_TASK, dto));
        checkResponse(response);
    }

    @Override
    public List<User> getAllWorkers() throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.GET_ALL_WORKERS, null));
        checkResponse(response);
        return (List<User>) response.getData();
    }

    @Override
    public List<Log> getWorkerLogs(Long workerId) throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.GET_WORKER_LOGS, workerId));
        checkResponse(response);
        return (List<Log>) response.getData();
    }

    @Override
    public List<Task> getAvailableTasksByPriority(Priority priority) throws ServiceException {
        Response response = sendAndReceive(new Request(RequestType.GET_AVAILABLE_TASKS_BY_PRIORITY, priority));
        checkResponse(response);
        return (List<Task>) response.getData();
    }

    @Override
    public void addObserver(ITaskObserver observer) {
        this.localObserver = observer;
    }

    @Override
    public void removeObserver(ITaskObserver observer) {
        if (this.localObserver == observer) this.localObserver = null;
    }
}