package org.example.taskbid.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.example.taskbid.dto.DevRecommenderScenarioResponse;
import org.example.taskbid.dto.DevSeedResponse;
import org.example.taskbid.service.DevRecommenderScenarioService;
import org.example.taskbid.service.DevSeedService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevSeedController {

    DevSeedService devSeedService;
    DevRecommenderScenarioService devRecommenderScenarioService;

    @PostMapping("/seed-recommender")
    public ResponseEntity<DevSeedResponse> seedRecommenderEpic() {
        return ResponseEntity.ok(devSeedService.seedRecommenderEpic());
    }

    @PostMapping("/recommender-scenario")
    public ResponseEntity<DevRecommenderScenarioResponse> runRecommenderScenario() {
        return ResponseEntity.ok(devRecommenderScenarioService.runScenario());
    }
}
