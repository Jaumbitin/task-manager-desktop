package com.portfolio.taskmanager.desktop.model;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskItem(UUID id, String title, String description, TaskStatus status, LocalDateTime createdAt) {}
