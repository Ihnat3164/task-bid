package org.example.taskbid.entity.enums;

public enum TaskViewSource {
    RECOMMENDATION,
    LIST,
    DIRECT;

    public static TaskViewSource fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return DIRECT;
        }

        try {
            return TaskViewSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DIRECT;
        }
    }
}
