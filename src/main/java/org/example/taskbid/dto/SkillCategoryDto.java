package org.example.taskbid.dto;

import lombok.Data;

import java.util.List;

@Data
public class SkillCategoryDto {

    private Long id;
    private String name;
    private List<SkillDto> skills;

}
