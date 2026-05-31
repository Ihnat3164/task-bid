package org.example.taskbid.entity.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ApplicationStatus {
    PENDING,
    APPROVED,
    ACCEPTED,
    REJECTED;

    @JsonValue
    public String publicName() {
        return this == ACCEPTED ? "APPROVED" : name();
    }

    public boolean isApproved() {
        return this == APPROVED || this == ACCEPTED;
    }
}
