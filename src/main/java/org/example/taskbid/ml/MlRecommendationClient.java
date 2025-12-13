package org.example.taskbid.ml;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.example.taskbid.dto.MlRecommendationRequest;
import org.example.taskbid.dto.MlRecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MlRecommendationClient {

    RestTemplate restTemplate;

    @NonFinal
    @Value("${ml.service.url:http://localhost:8000}")
    String mlServiceUrl;

    public List<Long> getRecommendations(MlRecommendationRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<MlRecommendationRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<MlRecommendationResponse> response = restTemplate.postForEntity(
                    mlServiceUrl + "/recommend",
                    entity,
                    MlRecommendationResponse.class
            );

            return Optional.ofNullable(response.getBody())
                    .map(MlRecommendationResponse::getRecommendedTaskIds)
                    .orElse(Collections.emptyList());
        } catch (RestClientException ex) {
            log.warn("Failed to get recommendations from ML service: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
