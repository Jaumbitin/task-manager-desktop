package com.portfolio.taskmanager.desktop;

import com.portfolio.taskmanager.desktop.api.ApiClient;
import com.portfolio.taskmanager.desktop.controller.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Objects;

public final class TaskManagerApp extends Application {
    private Stage stage;
    private ApiClient api;
    private Object activeController;

    @Override public void start(Stage stage) throws IOException {
        this.stage = stage;
        api = new ApiClient(System.getProperty("task.api.url",
                System.getenv().getOrDefault("TASK_API_URL", "http://localhost:8080")));
        stage.setTitle("Task Manager • Organize seu dia");
        stage.setMinWidth(960);
        stage.setMinHeight(680);
        showLogin("");
        stage.show();
    }
    public ApiClient api() { return api; }
    public Stage stage() { return stage; }
    public boolean isActive(Object controller) { return activeController == controller; }

    public void showLogin(String message) {
        api.clearSession();
        var loader = load("auth.fxml");
        AuthController controller = loader.getController();
        activeController = controller;
        controller.configure(this, message);
    }
    public void showDashboard(String email) {
        var loader = load("dashboard.fxml");
        DashboardController controller = loader.getController();
        activeController = controller;
        controller.configure(this, email);
    }
    private FXMLLoader load(String name) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/portfolio/taskmanager/desktop/" + name));
            Parent root = loader.load();
            if (stage.getScene() == null) {
                Scene scene = new Scene(root, 1180, 780);
                scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/portfolio/taskmanager/desktop/styles.css")).toExternalForm());
                stage.setScene(scene);
            } else stage.getScene().setRoot(root);
            return loader;
        } catch (IOException ex) { throw new IllegalStateException("Não foi possível abrir a tela " + name, ex); }
    }
    @Override public void stop() { if (api != null) api.close(); }
}
