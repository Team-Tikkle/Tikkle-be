package com.tikkle.investment.repository;

import com.tikkle.investment.entity.AiRecommendationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationHistoryRepository extends JpaRepository<AiRecommendationHistory, Long> {
}