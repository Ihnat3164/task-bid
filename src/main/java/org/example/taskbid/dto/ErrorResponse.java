package org.example.taskbid.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@AllArgsConstructor
@Getter
public class ErrorResponse {
    String code;
    String message;
    Instant timestamp;
}
