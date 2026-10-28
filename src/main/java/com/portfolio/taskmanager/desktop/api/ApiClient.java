package com.portfolio.taskmanager.desktop.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.taskmanager.desktop.model.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Only this class knows HTTP, JSON, route names and the Bearer header. */
public final class ApiClient implements AutoCloseable {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String baseUrl;
    private volatile String token;

    public ApiClient(String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Endereço da API inválido");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }
    public void setToken(String token) { this.token = token; }
    public void clearSession() { token = null; }

    public CompletableFuture<String> login(String email, String password) {
        return request("POST", "/api/auth/login", Map.of("email", email, "password", password), false)
                .thenApply(value -> {
                    String result = value.strip();
                    if (result.isBlank()) throw new ApiException(0, "A API devolveu uma sessão inválida.");
                    return result;
                });
    }
    public CompletableFuture<Void> register(String email, String password) {
        return request("POST", "/api/users", Map.of("email", email, "password", password), false).thenApply(body -> null);
    }
    public CompletableFuture<List<TaskItem>> listTasks() {
        return request("GET", "/api/tasks", null, true).thenApply(body -> read(body, new TypeReference<List<TaskItem>>() {}));
    }
    public CompletableFuture<TaskItem> create(TaskInput task) {
        return request("POST", "/api/tasks", task, true).thenApply(body -> read(body, new TypeReference<TaskItem>() {}));
    }
    public CompletableFuture<TaskItem> update(UUID id, TaskInput task) {
        return request("PUT", "/api/tasks/" + id, task, true).thenApply(body -> read(body, new TypeReference<TaskItem>() {}));
    }
    public CompletableFuture<TaskItem> changeStatus(UUID id, TaskStatus status) {
        return request("PATCH", "/api/tasks/" + id + "/status", Map.of("status", status), true)
                .thenApply(body -> read(body, new TypeReference<TaskItem>() {}));
    }
    public CompletableFuture<Void> delete(UUID id) {
        return request("DELETE", "/api/tasks/" + id, null, true).thenApply(body -> null);
    }
    private CompletableFuture<String> request(String method, String path, Object body, boolean authenticated) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json, text/plain, application/problem+json");
            if (authenticated) {
                String current = token;
                if (current == null || current.isBlank()) return CompletableFuture.failedFuture(new ApiException(401, "Entre novamente."));
                builder.header("Authorization", "Bearer " + current);
            }
            var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            if (body != null) builder.header("Content-Type", "application/json");
            return http.sendAsync(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300)
                            throw new ApiException(response.statusCode(), errorMessage(response.statusCode(), response.body()));
                        return response.body();
                    });
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }
    private <T> T read(String body, TypeReference<T> type) {
        try { return json.readValue(body, type); }
        catch (Exception ex) { throw new ApiException(0, "A API devolveu uma resposta inesperada. Tente atualizar."); }
    }
    private String errorMessage(int status, String body) {
        if (status == 401) return "E-mail ou senha incorretos, ou sessão expirada.";
        if (status == 403) return "Você não tem permissão para esta ação.";
        if (status == 404) return "Esta tarefa não está mais disponível. Atualize a lista.";
        if (status >= 500) return "O servidor encontrou um problema. Tente novamente.";
        try {
            JsonNode node = json.readTree(body);
            if (node.has("errors") && node.get("errors").isArray()) {
                List<String> errors = new ArrayList<>();
                node.get("errors").forEach(e -> errors.add(e.asText()));
                if (!errors.isEmpty()) return String.join("\n", errors);
            }
            if (node.hasNonNull("detail")) return node.get("detail").asText();
        } catch (Exception ignored) { }
        return status == 409 ? "Este e-mail já está cadastrado." : "Não foi possível concluir a ação (HTTP " + status + ").";
    }
    public static Throwable cause(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null)
            error = error.getCause();
        return error;
    }
    public static String friendlyMessage(Throwable error) {
        Throwable cause = cause(error);
        if (cause instanceof ApiException) return cause.getMessage();
        if (cause instanceof HttpTimeoutException) return "A API demorou para responder. Tente novamente.";
        return "Não foi possível conectar à API. Confira se o backend está rodando e tente novamente.";
    }
    @Override public void close() { http.shutdownNow(); }
}
