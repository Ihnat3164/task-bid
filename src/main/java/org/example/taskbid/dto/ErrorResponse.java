package org.example.taskbid.dto;

import lombok.AllArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
public class ErrorResponse {
    String code;
    String message;
    Instant timestamp;
}
