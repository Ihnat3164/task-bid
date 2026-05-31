package org.example.taskbid.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    OPEN,
    ASSIGNED,
    READY_FOR_WORK,
    IN_PROGRESS,
    READY_FOR_ACCEPTANCE,
    COMPLETED,
    DONE,
    CANCELLED;

    @JsonValue
    public String publicName() {
        return switch (this) {
            case READY_FOR_WORK -> "ASSIGNED";
            case READY_FOR_ACCEPTANCE -> "IN_PROGRESS";
            case DONE -> "COMPLETED";
            default -> name();
        };
    }

    public boolean isAssigned() {
        return this == ASSIGNED || this == READY_FOR_WORK;
    }

    public boolean isReadyForCompletion() {
        return this == READY_FOR_ACCEPTANCE;
    }

    public boolean isCompleted() {
        return this == COMPLETED || this == DONE;
    }
}
