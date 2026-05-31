package org.example.taskbid.dto;

import lombok.Builder;
import lombok.Data;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.enums.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
public class TaskApplicantDto {
    private Long applicationId;
    private UUID profileId;
    private String username;
    private String description;
    private String city;
    private String price;
    private Double averageRating;
    private Long reviewsCount;
    private List<Skill> skills;
    private LocalDateTime createdAt;
}
