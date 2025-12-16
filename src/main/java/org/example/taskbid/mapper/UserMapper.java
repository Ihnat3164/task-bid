package org.example.taskbid.mapper;

import lombok.RequiredArgsConstructor;
import org.example.taskbid.dto.OnboardingRequest;
import org.example.taskbid.dto.RegisterRequest;
import org.example.taskbid.entity.Profile;
import org.example.taskbid.entity.Skill;
import org.example.taskbid.entity.User;
import org.example.taskbid.entity.enums.Roles;
import org.example.taskbid.repositiry.SkillRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final PasswordEncoder passwordEncoder;
    private final SkillRepository skillRepository;

    public User mapUserFromRegisterRequest(RegisterRequest request) {
        return User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .build();
    }

    public Profile mapOnboardingDataToProfile(OnboardingRequest onboardingRequest, User user) {

        List<Skill> skills = new ArrayList<>();

        if (onboardingRequest.getRole().equals("EXECUTOR")) {
             skills = onboardingRequest.getSkillIds().stream()
                    .map(id -> skillRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Skill not found: " + id)))
                    .toList();
        }

        return Profile.builder()
                .user(user)
                .role(Roles.valueOf(onboardingRequest.getRole()))
                .city(onboardingRequest.getCity())
                .description(onboardingRequest.getDescription())
                .skills(skills)
                .build();
    }

    public void applyOnboardingDataToProfile(Profile profile, OnboardingRequest req) {
        profile.setRole(Roles.valueOf(req.getRole()));
        profile.setCity(req.getCity());
        profile.setDescription(req.getDescription());

        // 💡 гарантируем List<Long>
        List<Long> ids = req.getSkillIds() == null
                ? List.of()
                : req.getSkillIds();

        // 💡 ЖЁСТКАЯ ТИПИЗАЦИЯ
        List<Skill> skills = ids.isEmpty()
                ? List.of()
                : skillRepository.findAllById(ids);

        profile.getSkills().clear();
        profile.getSkills().addAll(skills); // ✅ теперь компилируется
    }

}