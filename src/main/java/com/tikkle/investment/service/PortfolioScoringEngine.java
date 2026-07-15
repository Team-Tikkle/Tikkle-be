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

/**
 * AI가 도출한 1차 후보군을 대상으로 실시간 업비트 시세 급등락, 유저의 테마/밈 선호도,
 * 분산 투자 여부 등을 정규화된 0~100점 기반 및 연속형(Sigmoid) 함수로 평가하고,
 * 사용자 성향에 따른 가중치(AHP)를 곱해 최종 타겟 코인을 선정하는 퀀트 스코어링 엔진입니다.
 */
@Slf4j
@Component
public class PortfolioScoringEngine {

    public record ScoredCandidate(AiRecommendationDto candidate, double score) {}

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

        // 사용자 성향 기반 동적 가중치(Weight) 할당 (총합 1.0)
        double wAi = 0.30;
        double wTheme = 0.25;
        double wTrend = 0.15;
        double wRisk = 0.20;
        double wDiv = 0.10;

        // 트렌드 성향에 따른 가중치 조정
        if (trendScore >= 8) { // FULL_TREND
            wTrend = 0.35;
            wAi -= 0.10;
            wRisk -= 0.10;
        } else if (trendScore <= 3) { // FUNDAMENTAL_ONLY
            wTrend = 0.05;
            wAi += 0.10;
        }

        // 위험 감수성에 따른 가중치 조정
        if (riskScore <= 3) { // SELL_IMMEDIATELY (보수적)
            wRisk = 0.35;
            wAi -= 0.05;
            wTheme -= 0.10;
        } else if (riskScore >= 8) { // BUY_MORE (공격적/역발상)
            wRisk = 0.25;
            wAi -= 0.05;
        }

        // 정규화 (총합이 정확히 1.0이 되도록)
        double totalWeight = wAi + wTheme + wTrend + wRisk + wDiv;
        wAi /= totalWeight;
        wTheme /= totalWeight;
        wTrend /= totalWeight;
        wRisk /= totalWeight;
        wDiv /= totalWeight;

        log.debug("[PortfolioScoringEngine] 적용된 최종 가중치: AI({}), Theme({}), Trend({}), Risk({}), Div({})",
                wAi, wTheme, wTrend, wRisk, wDiv);

        int candidateCount = macroCandidates.size();

        for (int i = 0; i < candidateCount; i++) {
            AiRecommendationDto dto = macroCandidates.get(i);
            UpbitTickerResponse ticker = tickerMap.get(dto.market());

            if (ticker == null) continue;

            double changeRate = ticker.signedChangeRate() != null ? ticker.signedChangeRate() : 0.0;
            boolean drop = false;

            // --- 1. Meme 컷오프 (하드 필터링) ---
            if (dto.isMeme() && memeMaxWeight == 0) {
                drop = true;
                log.debug("[PortfolioScoringEngine] 밈 코인 영구 탈락 - market: {}", dto.market());
                continue;
            }

            // --- 2. S_ai: AI 펀더멘탈 점수 (0~100 정규화) ---
            double sAi = ((candidateCount - 1 - i) / (double) Math.max(1, candidateCount - 1)) * 100.0;

            // --- 3. S_theme: 테마 적합도 점수 (0 or 100) ---
            double sTheme = (dto.theme() != null && userThemes.contains(dto.theme())) ? 100.0 : 0.0;

            // --- 4. S_trend: 모멘텀 점수 (연속형 Sigmoid) ---
            // 상승폭이 클수록 100점에 부드럽게 수렴 (k=20, 5% 상승 시 약 73점)
            double sTrend = (1.0 / (1.0 + Math.exp(-20.0 * changeRate))) * 100.0;

            // --- 5. S_risk: 리스크 방어/역발상 점수 (연속형 Sigmoid 및 패널티 함수) ---
            double sRisk = 50.0;
            if (riskScore <= 3) {
                // 보수적: 하락 시 패널티 기하급수적 증가 (-2% 이하로 떨어지면 점수 급감)
                sRisk = (1.0 / (1.0 + Math.exp(-50.0 * (changeRate + 0.02)))) * 100.0;
                if (changeRate <= -0.05) drop = true; // -5% 이하 하드 필터링 유지 (안전장치)
            } else if (riskScore >= 8) {
                // 공격적(역발상): 폭락 시 줍줍 기회로 판단하여 하락할수록 점수 급증
                sRisk = (1.0 / (1.0 + Math.exp(20.0 * changeRate))) * 100.0;
            } else {
                // 중립적: 완만한 리스크 함수
                sRisk = (1.0 / (1.0 + Math.exp(-20.0 * changeRate))) * 100.0;
            }

            // --- 6. S_div: 분산 투자 점수 (0 or 100) ---
            double sDiv = 50.0;
            if (pastPurchasedMarkets != null && !pastPurchasedMarkets.isEmpty()) {
                boolean isAlreadyOwned = pastPurchasedMarkets.contains(dto.market());
                if (divType == DiversificationType.CONCENTRATED) {
                    sDiv = isAlreadyOwned ? 100.0 : 0.0;
                } else if (divType == DiversificationType.DIVERSIFIED) {
                    sDiv = isAlreadyOwned ? 0.0 : 100.0;
                }
            }

            if (!drop) {
                // MCDA (Multi-Criteria Decision Analysis) 합산
                double finalScore = (wAi * sAi) + (wTheme * sTheme) + (wTrend * sTrend) + (wRisk * sRisk) + (wDiv * sDiv);
                
                // 밈 수용도가 높을 경우 약간의 프리미엄 점수 (최대 3점) 부여
                if (dto.isMeme() && memeMaxWeight == 30) finalScore += 3.0;

                log.debug("[PortfolioScoringEngine] 코인: {}, S_ai: {}, S_theme: {}, S_trend: {}, S_risk: {}, S_div: {} => Final: {}",
                        dto.market(), sAi, sTheme, sTrend, sRisk, sDiv, finalScore);
                
                scoredList.add(new ScoredCandidate(dto, finalScore));
            }
        }

        // 최종 점수 내림차순 정렬
        scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));

        return scoredList.stream()
                .map(ScoredCandidate::candidate)
                .toList();
    }
}