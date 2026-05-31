package org.example.taskbid.repositiry;

import org.example.taskbid.entity.RecommendationImpression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationImpressionRepository extends JpaRepository<RecommendationImpression, Long> {
    List<RecommendationImpression> findAllByOrderByRecommendedAtAscIdAsc();
}
