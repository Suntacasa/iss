package gui;

import domain.Log;
import domain.Priority;
import domain.Status;
import domain.Task;
import domain.User;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import service.ITaskObserver;
import service.ITaskService;
import service.ServiceException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class BossDashboardController implements ITaskObserver {

    private static final Logger logger = Logger.getLogger(BossDashboardController.class.getName());

    // ── Task table ─────────────────────────────────────────────────────────
    @FXML private TableView<Task> taskTable;
    @FXML private TableColumn<Task, String> colTitle;
    @FXML private TableColumn<Task, String> colDescription;
    @FXML private TableColumn<Task, String> colDeadline;
    @FXML private TableColumn<Task, String> colPriority;
    @FXML private TableColumn<Task, String> colStatus;

    // ── Create / Edit form ─────────────────────────────────────────────────
    @FXML private TextField titleField;
    @FXML private TextField descriptionField;
    @FXML private DatePicker deadlinePicker;
    @FXML private ComboBox<Priority> priorityCombo;

    // ── Status / info ──────────────────────────────────────────────────────
    @FXML private Label statusLabel;
    @FXML private Label userLabel;

    // ── UC-9: Priority filter ──────────────────────────────────────────────
    @FXML private ComboBox<String> priorityFilterCombo; // "ALL" + Priority values

    private ITaskService service;
    private User user;
    private Stage stage;

    public void setService(ITaskService service) { this.service = service; }
    public void setUser(User user) { this.user = user; }
    public void setStage(Stage stage) { this.stage = stage; }

    public void loadData() {
        setupTable();
        priorityCombo.setItems(FXCollections.observableArrayList(Priority.values()));

        // UC-9: filter options — "ALL" shows everything
        priorityFilterCombo.setItems(FXCollections.observableArrayList(
                "ALL", "LOW", "MEDIUM", "HIGH", "ASAP"));
        priorityFilterCombo.setValue("ALL");

        userLabel.setText("Logged in as: " + user.getUsername() + " (Boss)");
        service.addObserver(this);
        refreshTasks();
    }

    private void setupTable() {
        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colDescription.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colDeadline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDeadline().toString()));
        colPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority().name()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));

        // Click on row → populate edit form
        taskTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            if (selected != null) populateForm(selected);
        });
    }

    private void populateForm(Task task) {
        titleField.setText(task.getTitle());
        descriptionField.setText(task.getDescription());
        deadlinePicker.setValue(task.getDeadline());
        priorityCombo.setValue(task.getPriority());
    }

    private void clearForm() {
        titleField.clear();
        descriptionField.clear();
        deadlinePicker.setValue(null);
        priorityCombo.setValue(null);
        taskTable.getSelectionModel().clearSelection();
    }

    // UC-2: Create Task
    @FXML
    public void handleCreateTask() {
        String title = titleField.getText().trim();
        String desc = descriptionField.getText().trim();
        LocalDate deadline = deadlinePicker.getValue();
        Priority priority = priorityCombo.getValue();

        try {
            service.createTask(title, desc, deadline, priority);
            setStatus("Task created successfully.", false);
            clearForm();
            // table will refresh via push notification
        } catch (ServiceException e) {
            setStatus(e.getMessage(), true);
        }
    }

    // UC-3: Edit Task — only works if an AVAILABLE task is selected
    @FXML
    public void handleEditTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a task to edit.", true);
            return;
        }
        if (selected.getStatus() != Status.AVAILABLE) {
            setStatus("Only available (unassigned) tasks can be edited.", true);
            return;
        }

        String title = titleField.getText().trim();
        String desc = descriptionField.getText().trim();
        LocalDate deadline = deadlinePicker.getValue();
        Priority priority = priorityCombo.getValue();

        try {
            service.editTask(selected.getId(), title, desc, deadline, priority);
            setStatus("Task updated successfully.", false);
            clearForm();
        } catch (ServiceException e) {
            setStatus(e.getMessage(), true);
        }
    }

    // UC-4: Delete Task
    @FXML
    public void handleDeleteTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a task to delete.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete task \"" + selected.getTitle() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm deletion");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    Long affectedWorker = service.deleteTask(selected.getId());
                    if (affectedWorker != null) {
                        setStatus("Task deleted. Assigned worker has been notified.", false);
                    } else {
                        setStatus("Task deleted.", false);
                    }
                    clearForm();
                } catch (ServiceException e) {
                    setStatus(e.getMessage(), true);
                }
            }
        });
    }

    // UC-5: Boss assigns task to a specific worker via popup dialog
    @FXML
    public void handleAssignTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a task to assign.", true);
            return;
        }
        if (selected.getStatus() != Status.AVAILABLE) {
            setStatus("Only available tasks can be assigned.", true);
            return;
        }

        try {
            List<User> workers = service.getAllWorkers();
            if (workers.isEmpty()) {
                setStatus("No workers registered in the system.", true);
                return;
            }

            // Build a choice dialog listing all workers
            ChoiceDialog<User> dialog = new ChoiceDialog<>(workers.get(0), workers);
            dialog.setTitle("Assign Task");
            dialog.setHeaderText("Assign \"" + selected.getTitle() + "\" to:");
            dialog.setContentText("Select worker:");

            // Display username in the dialog
            dialog.getItems(); // already set via constructor
            // Override toString isn't possible on domain objects here, so use a workaround:
            dialog.getDialogPane().setExpandableContent(null);

            // Use a ListView-based dialog for cleaner display
            Dialog<User> workerDialog = new Dialog<>();
            workerDialog.setTitle("Assign Task");
            workerDialog.setHeaderText("Assign \"" + selected.getTitle() + "\" to a worker:");
            ButtonType assignBtn = new ButtonType("Assign", ButtonBar.ButtonData.OK_DONE);
            workerDialog.getDialogPane().getButtonTypes().addAll(assignBtn, ButtonType.CANCEL);

            ListView<User> listView = new ListView<>(FXCollections.observableArrayList(workers));
            listView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(User u, boolean empty) {
                    super.updateItem(u, empty);
                    setText(empty || u == null ? null : u.getUsername());
                }
            });
            listView.getSelectionModel().selectFirst();
            workerDialog.getDialogPane().setContent(listView);
            workerDialog.setResultConverter(btn ->
                    btn == assignBtn ? listView.getSelectionModel().getSelectedItem() : null);

            Optional<User> result = workerDialog.showAndWait();
            result.ifPresent(worker -> {
                try {
                    service.assignTaskToWorker(selected.getId(), worker.getId());
                    setStatus("Task assigned to " + worker.getUsername() + ".", false);
                } catch (ServiceException e) {
                    setStatus(e.getMessage(), true);
                }
            });

        } catch (ServiceException e) {
            setStatus("Error fetching workers: " + e.getMessage(), true);
        }
    }

    // UC-9: Filter task table by priority
    @FXML
    public void handleFilterByPriority() {
        String selected = priorityFilterCombo.getValue();
        try {
            List<Task> tasks;
            if (selected == null || selected.equals("ALL")) {
                tasks = service.getAllTasks();
            } else {
                // Boss sees all tasks of that priority (not just available)
                tasks = service.getAllTasks().stream()
                        .filter(t -> t.getPriority().name().equals(selected))
                        .toList();
            }
            taskTable.setItems(FXCollections.observableArrayList(tasks));
        } catch (ServiceException e) {
            setStatus("Error filtering tasks: " + e.getMessage(), true);
        }
    }

    // UC-10: Boss views a worker's log history — opens a popup
    @FXML
    public void handleViewWorkerLogs() {
        try {
            List<User> workers = service.getAllWorkers();
            if (workers.isEmpty()) {
                setStatus("No workers registered.", true);
                return;
            }

            Dialog<User> workerDialog = new Dialog<>();
            workerDialog.setTitle("View Worker Logs");
            workerDialog.setHeaderText("Select a worker to view their history:");
            ButtonType viewBtn = new ButtonType("View Logs", ButtonBar.ButtonData.OK_DONE);
            workerDialog.getDialogPane().getButtonTypes().addAll(viewBtn, ButtonType.CANCEL);

            ListView<User> listView = new ListView<>(FXCollections.observableArrayList(workers));
            listView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(User u, boolean empty) {
                    super.updateItem(u, empty);
                    setText(empty || u == null ? null : u.getUsername());
                }
            });
            listView.getSelectionModel().selectFirst();
            workerDialog.getDialogPane().setContent(listView);
            workerDialog.setResultConverter(btn ->
                    btn == viewBtn ? listView.getSelectionModel().getSelectedItem() : null);

            workerDialog.showAndWait().ifPresent(worker -> {
                try {
                    List<Log> logs = service.getWorkerLogs(worker.getId());
                    showLogsDialog(worker, logs);
                } catch (ServiceException e) {
                    setStatus("Error fetching logs: " + e.getMessage(), true);
                }
            });

        } catch (ServiceException e) {
            setStatus("Error fetching workers: " + e.getMessage(), true);
        }
    }

    private void showLogsDialog(User worker, List<Log> logs) {
        Dialog<Void> logsDialog = new Dialog<>();
        logsDialog.setTitle("Logs — " + worker.getUsername());
        logsDialog.setHeaderText("Task history for: " + worker.getUsername());
        logsDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<Log> logTable = new TableView<>();

        TableColumn<Log, String> colTask = new TableColumn<>("Task ID");
        colTask.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getTaskId())));

        TableColumn<Log, String> colDate = new TableColumn<>("Completed At");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCompletedAt().toString()));

        TableColumn<Log, String> colLate = new TableColumn<>("Late?");
        colLate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isLate() ? "YES ⚠" : "No"));

        logTable.getColumns().addAll(colTask, colDate, colLate);
        logTable.setItems(FXCollections.observableArrayList(logs));
        logTable.setPrefSize(450, 300);

        if (logs.isEmpty()) {
            logTable.setPlaceholder(new Label("No task history for this worker."));
        }

        logsDialog.getDialogPane().setContent(logTable);
        logsDialog.showAndWait();
    }

    // UC-12: Logout
    @FXML
    public void handleLogout() {
        try {
            service.removeObserver(this);
            service.logout(user);
        } catch (ServiceException e) {
            logger.warning("Logout error: " + e.getMessage());
        }
        stage.close();
        reopenLogin();
    }

    private void reopenLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
            Stage loginStage = new Stage();
            loginStage.setScene(new Scene(loader.load()));
            loginStage.setTitle("Task Manager — Login");
            LoginController ctrl = loader.getController();
            ctrl.setService(service);
            loginStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Push notification from server — task list changed
    @Override
    public void tasksUpdated(List<Task> tasks) throws ServiceException {
        Platform.runLater(() -> {
            Task selected = taskTable.getSelectionModel().getSelectedItem();
            Long selectedId = selected != null ? selected.getId() : null;

            taskTable.setItems(FXCollections.observableArrayList(tasks));

            // Re-select the previously selected row if still present
            if (selectedId != null) {
                tasks.stream()
                        .filter(t -> t.getId().equals(selectedId))
                        .findFirst()
                        .ifPresent(t -> taskTable.getSelectionModel().select(t));
            }
        });
    }

    private void refreshTasks() {
        try {
            List<Task> tasks = service.getAllTasks();
            taskTable.setItems(FXCollections.observableArrayList(tasks));
        } catch (ServiceException e) {
            setStatus("Error loading tasks: " + e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
    }
}