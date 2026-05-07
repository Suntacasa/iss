package gui;

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

import java.util.List;
import java.util.logging.Logger;

public class WorkerDashboardController implements ITaskObserver {

    private static final Logger logger = Logger.getLogger(WorkerDashboardController.class.getName());

    // ── My assigned tasks (UC-8) ───────────────────────────────────────────
    @FXML private TableView<Task> myTasksTable;
    @FXML private TableColumn<Task, String> colMyTitle;
    @FXML private TableColumn<Task, String> colMyDeadline;
    @FXML private TableColumn<Task, String> colMyPriority;
    @FXML private TableColumn<Task, String> colMyStatus;

    // ── Available tasks to browse (UC-6) ──────────────────────────────────
    @FXML private TableView<Task> availableTasksTable;
    @FXML private TableColumn<Task, String> colAvTitle;
    @FXML private TableColumn<Task, String> colAvDescription;
    @FXML private TableColumn<Task, String> colAvDeadline;
    @FXML private TableColumn<Task, String> colAvPriority;

    // ── Status / info ──────────────────────────────────────────────────────
    @FXML private Label statusLabel;
    @FXML private Label userLabel;
    @FXML private Label noTasksLabel;

    // ── UC-9: Priority filter ──────────────────────────────────────────────
    @FXML private ComboBox<String> priorityFilterCombo;

    private ITaskService service;
    private User user;
    private Stage stage;

    public void setService(ITaskService service) { this.service = service; }
    public void setUser(User user) { this.user = user; }
    public void setStage(Stage stage) { this.stage = stage; }

    public void loadData() {
        setupMyTasksTable();
        setupAvailableTasksTable();

        priorityFilterCombo.setItems(FXCollections.observableArrayList(
                "ALL", "LOW", "MEDIUM", "HIGH", "ASAP"));
        priorityFilterCombo.setValue("ALL");

        userLabel.setText("Logged in as: " + user.getUsername() + " (Worker)");
        service.addObserver(this);
        refreshDashboard();
        refreshAvailableTasks();
    }

    private void setupMyTasksTable() {
        colMyTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colMyDeadline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDeadline().toString()));
        colMyPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority().name()));
        colMyStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
    }

    private void setupAvailableTasksTable() {
        colAvTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colAvDescription.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colAvDeadline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDeadline().toString()));
        colAvPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority().name()));
    }

    // UC-6: Take a task from the available list
    @FXML
    public void handleTakeTask() {
        Task selected = availableTasksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select an available task to take.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Take task \"" + selected.getTitle() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm assignment");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    service.selfAssignTask(user.getId(), selected.getId());
                    setStatus("Task assigned to you successfully!", false);
                    // Both tables will refresh via push notification
                } catch (ServiceException e) {
                    // NFR-4: task was taken by someone else
                    setStatus(e.getMessage(), true);
                    refreshAvailableTasks(); // remove the now-gone task from the list
                }
            }
        });
    }

    // UC-7: Mark task as complete
    @FXML
    public void handleMarkComplete() {
        Task selected = myTasksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select one of your tasks to mark as complete.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Mark \"" + selected.getTitle() + "\" as complete?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm completion");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    service.markTaskComplete(user.getId(), selected.getId());
                    boolean wasLate = selected.getDeadline().isBefore(java.time.LocalDate.now());
                    setStatus("Task completed!" + (wasLate ? " (marked as late ⚠)" : ""), wasLate);
                } catch (ServiceException e) {
                    // UC-7 exception: task deleted by boss while worker was viewing
                    setStatus(e.getMessage(), true);
                    refreshDashboard();
                }
            }
        });
    }

    // UC-11: Drop task back to available
    @FXML
    public void handleDropTask() {
        Task selected = myTasksTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select one of your tasks to drop.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Drop \"" + selected.getTitle() + "\"? It will become available to other workers.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm drop");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    service.dropTask(user.getId(), selected.getId());
                    setStatus("Task dropped successfully.", false);
                } catch (ServiceException e) {
                    // UC-11 exception: task deleted by boss while worker was confirming
                    setStatus(e.getMessage(), true);
                    refreshDashboard();
                }
            }
        });
    }

    // UC-9: Filter available tasks by priority
    @FXML
    public void handleFilterByPriority() {
        String selected = priorityFilterCombo.getValue();
        try {
            List<Task> tasks;
            if (selected == null || selected.equals("ALL")) {
                tasks = service.getAvailableTasks();
            } else {
                tasks = service.getAvailableTasksByPriority(
                        domain.Priority.valueOf(selected));
            }
            availableTasksTable.setItems(FXCollections.observableArrayList(tasks));
        } catch (ServiceException e) {
            setStatus("Error filtering tasks: " + e.getMessage(), true);
        }
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

    // Push notification — task list changed (someone created/deleted/assigned a task)
    @Override
    public void tasksUpdated(List<Task> tasks) throws ServiceException {
        Platform.runLater(() -> {
            // Refresh available tasks from the pushed list
            List<Task> available = tasks.stream()
                    .filter(t -> t.getStatus() == domain.Status.AVAILABLE)
                    .toList();
            availableTasksTable.setItems(FXCollections.observableArrayList(available));

            // Refresh own dashboard separately (needs a server call for assigned tasks)
            refreshDashboard();

            // If a task the worker had was deleted, notify them
            setStatus("Task list updated.", false);
        });
    }

    private void refreshDashboard() {
        try {
            List<Task> myTasks = service.getWorkerDashboard(user.getId());
            myTasksTable.setItems(FXCollections.observableArrayList(myTasks));

            if (noTasksLabel != null) {
                noTasksLabel.setVisible(myTasks.isEmpty());
            }
        } catch (ServiceException e) {
            setStatus("Error loading your tasks: " + e.getMessage(), true);
        }
    }

    private void refreshAvailableTasks() {
        try {
            List<Task> available = service.getAvailableTasks();
            availableTasksTable.setItems(FXCollections.observableArrayList(available));
        } catch (ServiceException e) {
            setStatus("Error loading available tasks: " + e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setTextFill(isError ? Color.RED : Color.GREEN);
    }
}