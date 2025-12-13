package org.example.taskbid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlTaskPayload {
    private Long id;
    private String title;
    private String city;
    private List<MlSkillPayload> requiredSkills;
}