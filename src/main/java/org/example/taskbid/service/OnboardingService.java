package org.example.taskbid.service;

import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.example.taskbid.component.JwtUtil;
import org.example.taskbid.dto.OnboardingRequest;
import org.example.taskbid.dto.SkillCategoryDto;
import org.example.taskbid.dto.SkillDto;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.User;
import org.example.taskbid.mapper.UserMapper;
import org.example.taskbid.repositiry.ProfileRepository;
import org.example.taskbid.repositiry.SkillCategoryRepository;
import org.example.taskbid.repositiry.SkillRepository;
import org.example.taskbid.repositiry.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OnboardingService {

    SkillCategoryRepository skillCategoryRepository;
    ProfileRepository profileRepository;
    UserMapper userMapper;
    UserRepository userRep;
    JwtUtil jwtUtil;

    public List<SkillCategoryDto> getAllSkillCategories() {
        return skillCategoryRepository.findAll().stream().map(cat -> {
            SkillCategoryDto dto = new SkillCategoryDto();
            dto.setId(cat.getId());
            dto.setName(cat.getName());
            List<SkillDto> skills = cat.getSkills() == null ? List.of() :
                    cat.getSkills().stream().map(s -> {
                        SkillDto sd = new SkillDto();
                        sd.setId(s.getId());
                        sd.setName(s.getName());
                        return sd;
                    }).collect(Collectors.toList());
            dto.setSkills(skills);
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void onboardUser(OnboardingRequest req, String token) {
        User user = userRep.getUsersByEmail(jwtUtil.extractEmail(token));

        // ✅ находим существующий профиль по user_id, иначе создаём
        Profile profile = profileRepository.findByUser(user)
                .orElseGet(() -> {
                    Profile p = new Profile();
                    p.setId(UUID.randomUUID());
                    p.setUser(user);
                    return p;
                });

        // ✅ обновляем существующий профиль (НЕ создаём новый)
        userMapper.applyOnboardingDataToProfile(profile, req);

        profileRepository.save(profile);
        log.info("Onboarding saved: userId={}, profileId={}, role={}", user.getId(), profile.getId(), profile.getRole());
    }


}
