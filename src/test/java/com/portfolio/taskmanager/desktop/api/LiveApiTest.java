package com.portfolio.taskmanager.desktop.api;

import com.portfolio.taskmanager.desktop.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "task.integration", matches = "true")
class LiveApiTest {
    @Test void realBackendLifecycle() throws Exception {
        String email = "desktop-test-" + UUID.randomUUID() + "@example.com";
        try (ApiClient api = new ApiClient(System.getProperty("task.api.url", "http://localhost:8080"))) {
            api.register(email, "Desktop-test-123!").get(20, TimeUnit.SECONDS);
            api.setToken(api.login(email, "Desktop-test-123!").get(20, TimeUnit.SECONDS));
            var task = api.create(new TaskInput("Teste do desktop", "Registro temporário", TaskStatus.PENDING)).get(20, TimeUnit.SECONDS);
            try {
                assertTrue(api.listTasks().get(20, TimeUnit.SECONDS).stream().anyMatch(t -> t.id().equals(task.id())));
                var edited = api.update(task.id(), new TaskInput("Desktop integrado", "Editada", TaskStatus.IN_PROGRESS)).get(20, TimeUnit.SECONDS);
                assertEquals("Desktop integrado", edited.title());
                assertEquals(TaskStatus.IN_PROGRESS, edited.status());
                assertEquals(TaskStatus.COMPLETED, api.changeStatus(task.id(), TaskStatus.COMPLETED).get(20, TimeUnit.SECONDS).status());
            } finally { api.delete(task.id()).get(20, TimeUnit.SECONDS); }
            assertTrue(api.listTasks().get(20, TimeUnit.SECONDS).isEmpty());
            api.clearSession();
        }
    }
}
