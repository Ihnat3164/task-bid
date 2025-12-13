package org.example.taskbid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlProfilePayload {
    private UUID id;
    private String city;
    private Integer experience;
    private Integer workRadiusKm;
    private List<MlSkillPayload> skills;
}
