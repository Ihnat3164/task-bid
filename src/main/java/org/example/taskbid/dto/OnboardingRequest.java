package org.example.taskbid.dto;

import lombok.Data;
import lombok.Getter;

import java.util.List;

@Data
@Getter
public class OnboardingRequest {
    private String role;
    private String city;
    private String description;
    private List<Long> skillIds;
}
