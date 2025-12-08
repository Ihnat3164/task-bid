package org.example.taskbid.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.example.taskbid.dto.OnboardingRequest;
import org.example.taskbid.dto.SkillCategoryDto;
import org.example.taskbid.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OnboardingController {

    OnboardingService onboardingService;

    @GetMapping("/skills")
    public ResponseEntity<List<SkillCategoryDto>> getSkills() {
        return ResponseEntity.ok(onboardingService.getAllSkillCategories());
    }

    @PostMapping("/onboarding")
    public ResponseEntity<Void> onboarding(HttpServletRequest req, @RequestBody OnboardingRequest request) {
        String authHeader = req.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("JWT token is missing");
        }

        String token = authHeader.substring(7);

        log.info("Onboarding request received: {}", request);
        log.info("JWT token extracted: {}", token);
        onboardingService.onboardUser(request, token);
        return ResponseEntity.ok().build();
    }

}
