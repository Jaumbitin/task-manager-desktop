package com.portfolio.taskmanager.desktop;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.Stage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class FxmlSmokeTest {
    @BeforeAll static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(15, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }
    @AfterAll static void stop() { Platform.exit(); }
    @Test void bothScreensLoadWithControllersAndStyles() throws Exception {
        FutureTask<Void> test = new FutureTask<>(() -> {
            for (String file : new String[]{"auth.fxml", "dashboard.fxml"}) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/portfolio/taskmanager/desktop/" + file));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1180, 780);
                scene.getStylesheets().add(getClass().getResource("/com/portfolio/taskmanager/desktop/styles.css").toExternalForm());
                root.applyCss();
                root.layout();
                assertNotNull(loader.getController());
                assertFalse(root.getChildrenUnmodifiable().isEmpty());
            }
            return null;
        });
        Platform.runLater(test);
        test.get(20, TimeUnit.SECONDS);
    }

    @Test void dashboardLoadsTasksAndFiltersThem() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String data = """
                [
                  {"id":"00000000-0000-0000-0000-000000000001","title":"Organizar a semana","description":"Escolher as prioridades e reservar um tempo para cada uma.","status":"PENDING","createdAt":"2026-10-27T09:00:00"},
                  {"id":"00000000-0000-0000-0000-000000000002","title":"Dar vida à primeira interface","description":"Conectar as telas JavaFX à API de tarefas.","status":"IN_PROGRESS","createdAt":"2026-10-27T10:00:00"},
                  {"id":"00000000-0000-0000-0000-000000000003","title":"Testar o backend","description":"Cadastro, login e operações de tarefas funcionando.","status":"COMPLETED","createdAt":"2026-10-27T08:00:00"}
                ]
                """;
        server.createContext("/api/tasks", exchange -> {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String previous = System.getProperty("task.api.url");
        System.setProperty("task.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        TaskManagerApp app = new TaskManagerApp();
        Stage stage = fx(() -> {
            Stage window = new Stage();
            app.start(window);
            app.api().setToken("visual-test-token");
            app.showDashboard("preview@example.com");
            return window;
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!fx(() -> ((Label) stage.getScene().lookup("#resultCount")).getText().equals("3 tarefas"))
                    && System.nanoTime() < deadline) Thread.sleep(50);
            fx(() -> {
                assertEquals("3 tarefas", ((Label) stage.getScene().lookup("#resultCount")).getText());
                assertEquals("1", ((Label) stage.getScene().lookup("#completedCount")).getText());
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                WritableImage shot = stage.getScene().snapshot(null);
                int width = (int) shot.getWidth(), height = (int) shot.getHeight();
                int[] pixels = new int[width * height];
                shot.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, width, height, pixels, 0, width);
                Files.createDirectories(Path.of("work"));
                ImageIO.write(image, "png", Path.of("work/dashboard-preview.png").toFile());
                ((TextField) stage.getScene().lookup("#search")).setText("backend");
                assertEquals("1 tarefa", ((Label) stage.getScene().lookup("#resultCount")).getText());
                ((TextField) stage.getScene().lookup("#search")).clear();
                ((ComboBox<?>) stage.getScene().lookup("#statusFilter")).getSelectionModel().select(1);
                assertEquals("1 tarefa", ((Label) stage.getScene().lookup("#resultCount")).getText());
                return null;
            });
        } finally {
            fx(() -> { stage.close(); app.stop(); return null; });
            server.stop(0);
            if (previous == null) System.clearProperty("task.api.url");
            else System.setProperty("task.api.url", previous);
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
