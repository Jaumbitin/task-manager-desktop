package com.portfolio.taskmanager.desktop.controller;

import com.portfolio.taskmanager.desktop.TaskManagerApp;
import com.portfolio.taskmanager.desktop.api.*;
import com.portfolio.taskmanager.desktop.model.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class DashboardController {
    @FXML private Label emailLabel, totalCount, openCount, completedCount, resultCount, message, pageSubtitle;
    @FXML private TextField search;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ListView<TaskItem> taskList;
    @FXML private ProgressIndicator progress;
    @FXML private HBox actions;
    @FXML private VBox navigation;
    private final List<TaskItem> tasks = new ArrayList<>();
    private TaskManagerApp app;
    private boolean busy;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("pt-BR"));

    @FXML private void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList("Todos os status", "Pendentes", "Em andamento", "Concluídas"));
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((o, a, b) -> render());
        search.textProperty().addListener((o, a, b) -> render());
        taskList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(TaskItem task, boolean empty) {
                super.updateItem(task, empty);
                setText(null);
                setGraphic(empty || task == null ? null : card(task));
            }
        });
    }

    public void configure(TaskManagerApp app, String email) {
        this.app = app;
        emailLabel.setText(email);
        reload();
    }
    @FXML private void reload() {
        run(app.api().listTasks(), loaded -> {
            tasks.clear();
            tasks.addAll(loaded);
            render();
            message.setText("Tudo atualizado.");
        });
    }
    @FXML private void newTask() { openEditor(null); }
    @FXML private void logout() { app.showLogin(""); }
    @FXML private void showAll() { statusFilter.getSelectionModel().select(0); }
    @FXML private void showPending() { statusFilter.getSelectionModel().select(1); }
    @FXML private void showProgress() { statusFilter.getSelectionModel().select(2); }
    @FXML private void showCompleted() { statusFilter.getSelectionModel().select(3); }

    private void render() {
        if (taskList == null) return;
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.COMPLETED).count();
        totalCount.setText(String.valueOf(tasks.size()));
        openCount.setText(String.valueOf(tasks.size() - done));
        completedCount.setText(String.valueOf(done));
        int selected = statusFilter.getSelectionModel().getSelectedIndex();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        var visible = tasks.stream().filter(t -> selected <= 0 || t.status() == TaskStatus.values()[selected - 1])
                .filter(t -> (t.title() + " " + Objects.toString(t.description(), "")).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(TaskItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        taskList.setItems(FXCollections.observableArrayList(visible));
        resultCount.setText(visible.size() + (visible.size() == 1 ? " tarefa" : " tarefas"));
        Label title = new Label(tasks.isEmpty() ? "Seu próximo passo começa aqui." : "Nenhuma tarefa encontrada.");
        title.getStyleClass().add("empty-title");
        Label hint = new Label(tasks.isEmpty() ? "Crie sua primeira tarefa e tire uma ideia do papel." : "Experimente outro termo ou mude o filtro.");
        hint.getStyleClass().add("muted");
        VBox empty = new VBox(10, new Label("✓"), title, hint);
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("empty-state");
        taskList.setPlaceholder(empty);
        pageSubtitle.setText(tasks.isEmpty() ? "Um espaço tranquilo para organizar o que importa."
                : (tasks.size() - done) + " pendente(s) de conclusão. Um passo de cada vez.");
    }

    private Node card(TaskItem task) {
        Label title = new Label(task.title());
        title.getStyleClass().add("task-title");
        title.setWrapText(true);
        Label badge = new Label(task.status().toString());
        badge.getStyleClass().addAll("badge", task.status().css());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(14, title, gap, badge);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setMaxWidth(Double.MAX_VALUE);
        Label description = new Label(task.description() == null || task.description().isBlank() ? "Sem descrição" : task.description());
        description.getStyleClass().add("task-description");
        description.setWrapText(true);
        description.setMaxHeight(44);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        Label created = new Label(task.createdAt() == null ? "" : "Criada em " + task.createdAt().format(DATE));
        created.getStyleClass().add("small");
        Button edit = new Button("Editar");
        edit.getStyleClass().add("quiet-button");
        edit.setOnAction(e -> openEditor(task));
        Button complete = new Button(task.status() == TaskStatus.COMPLETED ? "Reabrir" : "✓  Concluir");
        complete.getStyleClass().add("soft-button");
        complete.setOnAction(e -> run(app.api().changeStatus(task.id(),
                task.status() == TaskStatus.COMPLETED ? TaskStatus.PENDING : TaskStatus.COMPLETED),
                updated -> replace(updated, "Status atualizado.")));
        Button delete = new Button("Excluir");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> confirmDelete(task));
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox footer = new HBox(8, created, space, edit, complete, delete);
        footer.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(12, top, description, footer);
        card.getStyleClass().add("task-card");
        return card;
    }

    private void confirmDelete(TaskItem task) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(app.stage());
        alert.setTitle("Excluir tarefa");
        alert.setHeaderText("Excluir “" + task.title() + "”?");
        alert.setContentText("Essa ação remove a tarefa permanentemente.");
        ButtonType delete = new ButtonType("Excluir", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, delete);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == delete) {
            run(app.api().delete(task.id()), ignored -> {
                tasks.removeIf(t -> t.id().equals(task.id()));
                render();
                message.setText("Tarefa excluída.");
            });
        }
    }

    private void openEditor(TaskItem existing) {
        if (busy) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(app.stage());
        dialog.setTitle(existing == null ? "Nova tarefa" : "Editar tarefa");
        dialog.setHeaderText(existing == null ? "O que você quer fazer?" : "Ajuste os detalhes da tarefa");
        dialog.getDialogPane().getStylesheets().add(app.stage().getScene().getStylesheets().getFirst());
        TextField title = new TextField(existing == null ? "" : existing.title());
        title.setPromptText("Ex.: Estudar Java por 30 minutos");
        title.setAccessibleText("Título da tarefa");
        TextArea description = new TextArea(existing == null ? "" : Objects.toString(existing.description(), ""));
        description.setPromptText("Adicione um contexto, se quiser...");
        description.setAccessibleText("Descrição da tarefa");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        ComboBox<TaskStatus> status = new ComboBox<>(FXCollections.observableArrayList(TaskStatus.values()));
        status.setValue(existing == null ? TaskStatus.PENDING : existing.status());
        status.setAccessibleText("Status da tarefa");
        status.setMaxWidth(Double.MAX_VALUE);
        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        VBox fields = new VBox(10, fieldLabel("Título", title), title, fieldLabel("Descrição", description), description,
                fieldLabel("Status", status), status, error);
        fields.setPrefWidth(470);
        fields.setPadding(new Insets(10, 0, 12, 0));
        dialog.getDialogPane().setContent(fields);
        ButtonType save = new ButtonType("Salvar tarefa", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.getStyleClass().add("primary-button");
        boolean[] saving = {false};
        dialog.setOnCloseRequest(e -> { if (saving[0]) e.consume(); });
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (title.getText().isBlank()) { error.setText("Dê um título para a tarefa."); title.requestFocus(); return; }
            if (title.getText().strip().length() > 255) { error.setText("Use no máximo 255 caracteres no título."); return; }
            if (description.getText().strip().length() > 2000) { error.setText("Use no máximo 2000 caracteres na descrição."); return; }
            saving[0] = true;
            fields.setDisable(true);
            saveButton.setDisable(true);
            dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            saveButton.setText("Salvando...");
            error.setText("");
            TaskInput input = new TaskInput(title.getText().strip(), description.getText().strip(), status.getValue());
            var future = existing == null ? app.api().create(input) : app.api().update(existing.id(), input);
            future.whenComplete((result, failure) -> Platform.runLater(() -> {
                saving[0] = false;
                if (!app.isActive(this)) { dialog.close(); return; }
                if (failure == null) {
                    replace(result, existing == null ? "Tarefa criada. Vamos começar?" : "Alterações salvas.");
                    dialog.close();
                } else if (isUnauthorized(failure)) {
                    dialog.close();
                    app.showLogin("Sua sessão expirou. Entre novamente.");
                } else {
                    fields.setDisable(false);
                    saveButton.setDisable(false);
                    dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
                    saveButton.setText("Salvar tarefa");
                    error.setText(ApiClient.friendlyMessage(failure));
                }
            }));
        });
        dialog.setOnShown(e -> title.requestFocus());
        dialog.showAndWait();
    }

    private Label fieldLabel(String text, Node field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        label.getStyleClass().add("field-label");
        return label;
    }
    private void replace(TaskItem task, String feedback) {
        tasks.removeIf(t -> t.id().equals(task.id()));
        tasks.add(task);
        render();
        message.setText(feedback);
    }
    private <T> void run(CompletableFuture<T> future, Consumer<T> success) {
        setBusy(true);
        message.setText("Sincronizando...");
        future.whenComplete((result, error) -> Platform.runLater(() -> {
            if (!app.isActive(this)) return;
            setBusy(false);
            if (error == null) success.accept(result);
            else if (isUnauthorized(error)) app.showLogin("Sua sessão expirou. Entre novamente.");
            else message.setText(ApiClient.friendlyMessage(error));
        }));
    }
    private boolean isUnauthorized(Throwable failure) {
        return ApiClient.cause(failure) instanceof ApiException error && error.status() == 401;
    }
    private void setBusy(boolean busy) {
        this.busy = busy;
        actions.setDisable(busy);
        taskList.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
    }
}
