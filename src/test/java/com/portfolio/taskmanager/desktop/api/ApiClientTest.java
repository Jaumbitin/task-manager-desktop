package com.portfolio.taskmanager.desktop.api;

import com.sun.net.httpserver.*;
import com.portfolio.taskmanager.desktop.model.*;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ApiClientTest {
    HttpServer server;
    ApiClient api;
    AtomicReference<String> method = new AtomicReference<>(), path = new AtomicReference<>(),
            authorization = new AtomicReference<>(), body = new AtomicReference<>();
    int responseStatus = 200;
    String responseBody = "";
    UUID taskId = UUID.randomUUID();
    String taskJson() {
        return "{\"id\":\"" + taskId + "\",\"title\":\"Estudar Java\",\"description\":\"API\",\"status\":\"PENDING\",\"createdAt\":\"2026-10-27T12:00:00\"}";
    }

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, responseStatus == 204 ? -1 : bytes.length);
            if (responseStatus != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        api = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { api.close(); server.stop(0); }

    @Test void loginReadsPlainTokenWithoutSendingOldAuthorization() throws Exception {
        responseBody = "example.jwt.token";
        api.setToken("old-token");
        assertEquals(responseBody, api.login("teste@example.com", "senha").get(5, TimeUnit.SECONDS));
        assertEquals("/api/auth/login", path.get());
        assertNull(authorization.get());
        assertTrue(body.get().contains("\"email\":\"teste@example.com\""));
    }
    @Test void listParsesStatusesAndDatesAndSendsBearer() throws Exception {
        responseBody = "[" + taskJson() + "]";
        api.setToken("test-token");
        var items = api.listTasks().get(5, TimeUnit.SECONDS);
        assertEquals("Bearer test-token", authorization.get());
        assertEquals(TaskStatus.PENDING, items.getFirst().status());
        assertEquals(2026, items.getFirst().createdAt().getYear());
    }
    @Test void createsUpdatesCompletesAndDeletesUsingExpectedContract() throws Exception {
        api.setToken("test-token");
        responseBody = taskJson();
        var input = new TaskInput("Estudar Java", "API", TaskStatus.PENDING);
        assertEquals(taskId, api.create(input).get(5, TimeUnit.SECONDS).id());
        assertEquals("POST", method.get());
        assertTrue(body.get().contains("\"status\":\"PENDING\""));
        api.update(taskId, input).get(5, TimeUnit.SECONDS);
        assertEquals("PUT", method.get());
        assertEquals("/api/tasks/" + taskId, path.get());
        api.changeStatus(taskId, TaskStatus.COMPLETED).get(5, TimeUnit.SECONDS);
        assertEquals("PATCH", method.get());
        assertEquals("/api/tasks/" + taskId + "/status", path.get());
        assertTrue(body.get().contains("COMPLETED"));
        responseStatus = 204;
        api.delete(taskId).get(5, TimeUnit.SECONDS);
        assertEquals("DELETE", method.get());
    }
    @Test void mapsValidationAndUnauthorizedErrors() {
        responseStatus = 400;
        responseBody = "{\"detail\":\"Dados invalidos\",\"errors\":[\"title: obrigatório\"]}";
        api.setToken("test-token");
        var failure = assertThrows(ExecutionException.class, () -> api.create(new TaskInput("", "", TaskStatus.PENDING)).get(5, TimeUnit.SECONDS));
        assertEquals("title: obrigatório", ApiClient.friendlyMessage(failure));
        responseStatus = 401;
        var denied = assertThrows(ExecutionException.class, () -> api.listTasks().get(5, TimeUnit.SECONDS));
        assertEquals(401, ((ApiException) ApiClient.cause(denied)).status());
    }
    @Test void missingSessionNeverSendsRequest() {
        assertThrows(ExecutionException.class, () -> api.listTasks().get(5, TimeUnit.SECONDS));
        assertNull(path.get());
    }
    @Test void malformedJsonHasUnderstandableMessage() {
        api.setToken("test-token");
        responseBody = "not-json";
        var failure = assertThrows(ExecutionException.class, () -> api.listTasks().get(5, TimeUnit.SECONDS));
        assertTrue(ApiClient.friendlyMessage(failure).contains("resposta inesperada"));
    }

    @Test void emptyLoginResponseIsRejected() {
        responseBody = " ";
        var failure = assertThrows(ExecutionException.class, () -> api.login("test@example.com", "password").get(5, TimeUnit.SECONDS));
        assertTrue(ApiClient.friendlyMessage(failure).contains("sessão inválida"));
    }
}
