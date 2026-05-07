import gui.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.ServerProxy;
import service.ServiceException;

public class StartClient extends Application {

    private static final String HOST = "localhost";
    private static final int PORT = 5557;

    private ServerProxy proxy;

    @Override
    public void start(Stage primaryStage) throws Exception {
        proxy = new ServerProxy(HOST, PORT);
        try {
            proxy.connect();
        } catch (ServiceException e) {
            System.err.println("Cannot connect to server: " + e.getMessage());
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        primaryStage.setTitle("Task Manager — Login");

        LoginController loginController = loader.getController();
        loginController.setService(proxy);

        primaryStage.setOnCloseRequest(e -> {
            try { proxy.logout(null); } catch (Exception ignored) {}
        });

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}