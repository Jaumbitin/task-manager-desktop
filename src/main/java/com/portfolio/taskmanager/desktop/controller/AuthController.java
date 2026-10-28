package com.portfolio.taskmanager.desktop.controller;

import com.portfolio.taskmanager.desktop.TaskManagerApp;
import com.portfolio.taskmanager.desktop.api.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class AuthController {
    @FXML private Label heading, subtitle, message, switchPrompt;
    @FXML private TextField email;
    @FXML private PasswordField password, confirmation;
    @FXML private VBox confirmGroup, form;
    @FXML private Button submit, switchMode;
    @FXML private ProgressIndicator progress;
    private TaskManagerApp app;
    private boolean registering;

    public void configure(TaskManagerApp app, String initialMessage) {
        this.app = app;
        message.setText(initialMessage);
        Platform.runLater(email::requestFocus);
    }
    @FXML private void toggleMode() {
        registering = !registering;
        heading.setText(registering ? "Crie sua conta" : "Bom ter você por aqui.");
        subtitle.setText(registering ? "Um novo espaço para organizar suas ideias." : "Entre para continuar de onde parou.");
        confirmGroup.setVisible(registering);
        confirmGroup.setManaged(registering);
        submit.setText(registering ? "Criar conta" : "Entrar na minha conta");
        switchPrompt.setText(registering ? "Já tem uma conta?" : "Ainda não tem uma conta?");
        switchMode.setText(registering ? "Entrar" : "Criar conta");
        message.setText("");
        password.clear();
        confirmation.clear();
    }
    @FXML private void submit() {
        String address = email.getText().strip();
        String secret = password.getText();
        if (!address.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) { message.setText("Informe um e-mail válido."); email.requestFocus(); return; }
        if (secret.isBlank()) { message.setText("Informe sua senha."); password.requestFocus(); return; }
        if (address.length() > 254) { message.setText("O e-mail deve ter no máximo 254 caracteres."); return; }
        if (registering && secret.length() < 8) { message.setText("Use uma senha com pelo menos 8 caracteres."); return; }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) { message.setText("A senha ultrapassa o limite de 72 bytes. Use uma senha menor."); return; }
        if (registering && !secret.equals(confirmation.getText())) { message.setText("As senhas precisam ser iguais."); return; }
        boolean creating = registering;
        setBusy(true);
        message.setText("");
        var future = creating ? app.api().register(address, secret).thenApply(v -> "")
                : app.api().login(address, secret);
        future.whenComplete((token, error) -> Platform.runLater(() -> {
            if (!app.isActive(this)) return;
            setBusy(false);
            if (error != null) { message.setText(ApiClient.friendlyMessage(error)); return; }
            password.clear();
            confirmation.clear();
            if (creating) {
                toggleMode();
                message.setText("Conta criada! Agora entre com seu e-mail e senha.");
            } else {
                app.api().setToken(token);
                app.showDashboard(address);
            }
        }));
    }
    private void setBusy(boolean busy) {
        form.setDisable(busy);
        switchMode.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
    }
}
