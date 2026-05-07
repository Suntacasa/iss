package network;

import domain.Task;
import domain.User;
import network.*;
import service.ITaskObserver;
import service.ITaskService;
import service.ServiceException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientWorker implements Runnable, ITaskObserver {

    private static final Logger logger = Logger.getLogger(ClientWorker.class.getName());

    private final ITaskService service;
    private final Socket clientSocket;

    private ObjectInputStream input;
    private ObjectOutputStream output;
    private volatile boolean connected = true;

    public ClientWorker(ITaskService service, Socket clientSocket) {
        this.service = service;
        this.clientSocket = clientSocket;
        try {
            // Output must be created before input to avoid deadlock
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(clientSocket.getInputStream());
            logger.info("ClientWorker created for: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error creating streams", e);
        }
    }

    @Override
    public void run() {
        while (connected) {
            try {
                Request request = (Request) input.readObject();
                logger.info("Request received: " + request.getType());
                Response response = handleRequest(request);
                if (response != null) sendResponse(response);
            } catch (IOException | ClassNotFoundException e) {
                logger.info("Client disconnected: " + e.getMessage());
                connected = false;
            }
        }
        service.removeObserver(this);
        try { clientSocket.close(); } catch (IOException ignored) {}
        logger.info("ClientWorker stopped");
    }

    private Response handleRequest(Request request) {
        try {
            switch (request.getType()) {

                case LOGIN -> {
                    User user = (User) request.getData();
                    User loggedIn = service.login(user.getUsername(), user.getPassword());
                    service.addObserver(this);
                    return Response.ok(loggedIn);
                }

                case LOGOUT -> {
                    User user = (User) request.getData();
                    service.logout(user);
                    service.removeObserver(this);
                    connected = false;
                    return Response.ok(null);
                }

                case CREATE_TASK -> {
                    CreateTaskDTO dto = (CreateTaskDTO) request.getData();
                    Task task = service.createTask(dto.getTitle(), dto.getDescription(), dto.getDeadline(), dto.getPriority());
                    return Response.ok(task);
                }

                case EDIT_TASK -> {
                    EditTaskDTO dto = (EditTaskDTO) request.getData();
                    service.editTask(dto.getTaskId(), dto.getTitle(), dto.getDescription(), dto.getDeadline(), dto.getPriority());
                    return Response.ok(null);
                }

                case DELETE_TASK -> {
                    Long taskId = (Long) request.getData();
                    Long affectedUserId = service.deleteTask(taskId);
                    return Response.ok(affectedUserId); // client can check if it was their task
                }

                case GET_ALL_TASKS -> {
                    List<Task> tasks = service.getAllTasks();
                    return Response.ok(tasks);
                }

                case GET_AVAILABLE_TASKS -> {
                    List<Task> tasks = service.getAvailableTasks();
                    return Response.ok(tasks);
                }

                case GET_WORKER_DASHBOARD -> {
                    Long workerId = (Long) request.getData();
                    List<Task> tasks = service.getWorkerDashboard(workerId);
                    return Response.ok(tasks);
                }

                case SELF_ASSIGN_TASK -> {
                    SelfAssignDTO dto = (SelfAssignDTO) request.getData();
                    service.selfAssignTask(dto.getWorkerId(), dto.getTaskId());
                    return Response.ok(null);
                }

                case ASSIGN_TASK_TO_WORKER -> {
                    AssignTaskDTO dto = (AssignTaskDTO) request.getData();
                    service.assignTaskToWorker(dto.getTaskId(), dto.getWorkerId());
                    return Response.ok(null);
                }

                case MARK_TASK_COMPLETE -> {
                    MarkCompleteDTO dto = (MarkCompleteDTO) request.getData();
                    service.markTaskComplete(dto.getWorkerId(), dto.getTaskId());
                    return Response.ok(null);
                }

                case DROP_TASK -> {
                    DropTaskDTO dto = (DropTaskDTO) request.getData();
                    service.dropTask(dto.getWorkerId(), dto.getTaskId());
                    return Response.ok(null);
                }

                case GET_ALL_WORKERS -> {
                    return Response.ok(service.getAllWorkers());
                }

                case GET_WORKER_LOGS -> {
                    Long workerId = (Long) request.getData();
                    return Response.ok(service.getWorkerLogs(workerId));
                }

                case GET_AVAILABLE_TASKS_BY_PRIORITY -> {
                    domain.Priority priority = (domain.Priority) request.getData();
                    return Response.ok(service.getAvailableTasksByPriority(priority));
                }

                default -> {
                    return Response.error("Unknown request type: " + request.getType());
                }
            }
        } catch (ServiceException e) {
            logger.warning("ServiceException handling request: " + e.getMessage());
            return Response.error(e.getMessage());
        }
    }

    // Called by the server on this observer when task list changes
    @Override
    public void tasksUpdated(List<Task> tasks) throws ServiceException {
        try {
            sendResponse(Response.tasksUpdated(tasks));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending push notification to client", e);
            throw new ServiceException("Error notifying client", e);
        }
    }

    private synchronized void sendResponse(Response response) throws IOException {
        output.writeObject(response);
        output.flush();
    }
}