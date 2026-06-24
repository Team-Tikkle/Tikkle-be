package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.entity.enums.DiversificationType;
import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PortfolioScoringEngine {

    public record ScoredCandidate(AiRecommendationDto candidate, int score) {}

    public List<AiRecommendationDto> selectRealtimeTargets(
            List<AiRecommendationDto> macroCandidates,
            List<UpbitTickerResponse> realTimeTickers,
            InvestmentProfile profile,
            List<String> pastPurchasedMarkets) {

        if (macroCandidates == null || macroCandidates.isEmpty()) {
            return List.of();
        }

        Map<String, UpbitTickerResponse> tickerMap = realTimeTickers.stream()
                .collect(Collectors.toMap(UpbitTickerResponse::market, t -> t));

        List<ScoredCandidate> scoredList = new ArrayList<>();

        int trendScore = profile.getTrendSensitivity().getScore();
        int riskScore = profile.getRiskTolerance().getScore();
        int memeMaxWeight = profile.getMemeAcceptance().getMaxWeightPercent();
        List<String> userThemes = profile.getCryptoThemes().stream().map(Enum::name).toList();
        DiversificationType divType = profile.getDiversificationType();

        for (int i = 0; i < macroCandidates.size(); i++) {
            AiRecommendationDto dto = macroCandidates.get(i);
            UpbitTickerResponse ticker = tickerMap.get(dto.market());

            if (ticker == null) {
                continue;
            }

            double changeRate = ticker.signedChangeRate() != null ? ticker.signedChangeRate() : 0.0;
            // Base score based on AI's fundamental ranking
            int score = 100 - i;
            boolean drop = false;

            // --- Q5: Meme Acceptance (백엔드 컷오프) ---
            if (dto.isMeme()) {
                if (memeMaxWeight == 0) {
                    drop = true;
                    log.debug("[MEME_CUTOFF] Dropped meme coin {}", dto.market());
                    continue;
                } else if (memeMaxWeight == 30) {
                    score += 10; // ACTIVE면 밈 코인에 약간의 가산점
                }
            }

            // --- Q3: Crypto Themes (백엔드 테마 가중치) ---
            if (dto.theme() != null && userThemes.contains(dto.theme())) {
                score += 200; // 유저의 관심 테마와 일치하면 압도적 가산점
                log.debug("[THEME_MATCH] +200 pts for {} (Theme: {})", dto.market(), dto.theme());
            }

            // --- Q2: Trend Sensitivity (트렌드 추종) ---
            if (trendScore >= 8) { // FULL_TREND
                if (changeRate >= 0.05) {
                    score += 50; // 당일 급등 시 탑승
                    log.debug("[FULL_TREND] +50 pts for {} ({}%)", dto.market(), changeRate * 100);
                }
            } else if (trendScore <= 3) { // FUNDAMENTAL_ONLY
                if (changeRate >= 0.05) {
                    score -= 50; // 급등주 FOMO 회피
                    log.debug("[FUND_ONLY] -50 pts for {} ({}%)", dto.market(), changeRate * 100);
                }
            }

            // --- Q1: Risk Tolerance (하락장 방어) ---
            if (riskScore <= 3) { // SELL_IMMEDIATELY
                if (changeRate <= -0.05) {
                    drop = true; // -5% 이상 하락 코인은 즉각 배제
                    log.debug("[SELL_IMM] Dropped {} ({}%)", dto.market(), changeRate * 100);
                }
            } else if (riskScore >= 8) { // BUY_MORE
                if (changeRate <= -0.1) {
                    score += 50; // -10% 이상 하락 시 줍줍 가산점
                    log.debug("[BUY_MORE] +50 pts for {} ({}%)", dto.market(), changeRate * 100);
                }
            }

            // --- Q4: Diversification (시간에 따른 포트폴리오 분산도) ---
            if (pastPurchasedMarkets != null && !pastPurchasedMarkets.isEmpty()) {
                boolean isAlreadyOwned = pastPurchasedMarkets.contains(dto.market());
                
                if (divType == DiversificationType.CONCENTRATED) {
                    if (isAlreadyOwned) {
                        score += 100; // 기존에 샀던 코인에 대폭 가산 (한 우물 파기)
                        log.debug("[DIV_CONCENTRATED] +100 pts for {} (Already owned)", dto.market());
                    }
                } else if (divType == DiversificationType.DIVERSIFIED) {
                    if (isAlreadyOwned) {
                        score -= 100; // 기존에 산 코인은 감점 (새로운 코인 순환 매수 유도)
                        log.debug("[DIV_DIVERSIFIED] -100 pts for {} (Already owned)", dto.market());
                    }
                }
            }

            if (!drop) {
                scoredList.add(new ScoredCandidate(dto, score));
            }
        }

        // 최종 점수 내림차순 정렬
        scoredList.sort((a, b) -> Integer.compare(b.score(), a.score()));

        // 상위 타겟 리턴 (PaymentService에서 1순위 타겟을 골라서 매수)
        return scoredList.stream()
                .map(ScoredCandidate::candidate)
                .toList();
    }
}