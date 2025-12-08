package org.example.taskbid.dto;

import lombok.Data;

import java.util.List;

@Data
public class OnboardingRequest {
    private String role;
    private String city;
    private Integer experience;
    private String description;
    private Integer workRadiusKm;
    private List<Long> skillIds;
}
