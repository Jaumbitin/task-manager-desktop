package com.portfolio.taskmanager.desktop.model;

public enum TaskStatus {
    PENDING("Pendente", "pending"),
    IN_PROGRESS("Em andamento", "progress"),
    COMPLETED("Concluída", "completed");

    private final String label;
    private final String css;
    TaskStatus(String label, String css) { this.label = label; this.css = css; }
    public String css() { return css; }
    @Override public String toString() { return label; }
}
