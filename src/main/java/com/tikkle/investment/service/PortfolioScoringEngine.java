package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.entity.enums.DiversificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PortfolioScoringEngine {
    private static final int[] BASE_SCORES = {100, 80, 60, 40, 20};
    private static final int BONUS_SCORE = 30;

    public AiRecommendationDto selectBestRecommendation(
            List<AiRecommendationDto> recommendations,
            List<Portfolio> userPortfolios,
            DiversificationType diversificationType) {

        if (recommendations == null || recommendations.isEmpty()) {
            return null;
        }

        Set<String> portfolioTickers = userPortfolios.stream()
                .map(Portfolio::getTicker)
                .collect(Collectors.toSet());

        AiRecommendationDto bestRecommendation = recommendations.get(0);
        int highestScore = -1;
        int bestRank = Integer.MAX_VALUE;

        for (int i = 0; i < Math.min(recommendations.size(), BASE_SCORES.length); i++) {
            AiRecommendationDto dto = recommendations.get(i);
            int baseScore = BASE_SCORES[i];
            int bonusScore = 0;

            if (!portfolioTickers.isEmpty() && diversificationType != null) {
                boolean inPortfolio = portfolioTickers.contains(dto.ticker());

                if (diversificationType == DiversificationType.DIVERSIFIED && !inPortfolio) {
                    bonusScore = BONUS_SCORE;
                } else if (diversificationType == DiversificationType.CONCENTRATED && inPortfolio) {
                    bonusScore = BONUS_SCORE;
                }
            }

            int totalScore = baseScore + bonusScore;

            if (totalScore > highestScore) {
                highestScore = totalScore;
                bestRecommendation = dto;
                bestRank = i;
            } else if (totalScore == highestScore) {
                // Tie-breaker: AI rank (lower index is better)
                if (i < bestRank) {
                    bestRecommendation = dto;
                    bestRank = i;
                }
            }
        }

        return bestRecommendation;
    }
}