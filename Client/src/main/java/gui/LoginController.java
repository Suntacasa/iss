package gui;

import domain.Role;
import domain.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import service.ITaskService;
import service.ServiceException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private ITaskService service;

    public void setService(ITaskService service) {
        this.service = service;
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Username and password are required.");
            return;
        }

        try {
            User user = service.login(username, password);

            // Route to the correct dashboard based on role
            if (user.getRole() == Role.BOSS) {
                openBossDashboard(user);
            } else {
                openWorkerDashboard(user);
            }

            // Close login window
            Stage loginStage = (Stage) usernameField.getScene().getWindow();
            loginStage.close();

        } catch (ServiceException e) {
            errorLabel.setText(e.getMessage());
        } catch (Exception e) {
            errorLabel.setText("Error opening dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openBossDashboard(User user) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/boss_dashboard.fxml"));
        Stage stage = new Stage();
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Task Manager — Boss Dashboard (" + user.getUsername() + ")");

        BossDashboardController ctrl = loader.getController();
        ctrl.setService(service);
        ctrl.setUser(user);
        ctrl.setStage(stage);
        ctrl.loadData();

        stage.show();
    }

    private void openWorkerDashboard(User user) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/worker_dashboard.fxml"));
        Stage stage = new Stage();
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Task Manager — Worker Dashboard (" + user.getUsername() + ")");

        WorkerDashboardController ctrl = loader.getController();
        ctrl.setService(service);
        ctrl.setUser(user);
        ctrl.setStage(stage);
        ctrl.loadData();

        stage.show();
    }
}