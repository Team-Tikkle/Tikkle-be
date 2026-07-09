package com.tikkle.investment.repository;

import com.tikkle.investment.entity.AiRecommendationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiRecommendationHistoryRepository extends JpaRepository<AiRecommendationHistory, Long> {
    Optional<AiRecommendationHistory> findTopByProfileHashKeyOrderByIdDesc(String profileHashKey);
}