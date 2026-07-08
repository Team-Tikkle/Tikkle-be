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
 * 분산 투자 여부 등을 종합적으로 가감점 처리하여 최종 타겟 코인을 선정하는 퀀트 스코어링 엔진입니다.
 */
@Slf4j
@Component
public class PortfolioScoringEngine {

    public record ScoredCandidate(AiRecommendationDto candidate, int score) {}

    /**
     * 실시간 시세와 사용자 세부 성향을 반영하여 후보군을 스코어링하고 내림차순 정렬하여 반환합니다.
     *
     * @param macroCandidates AI가 생성한 1차 후보군 리스트 (15개)
     * @param realTimeTickers 업비트에서 조회한 실시간 시세 리스트
     * @param profile 사용자 투자 성향 프로필
     * @param pastPurchasedMarkets 과거 결제(매수)한 이력이 있는 마켓 코드 리스트
     * @return 최종 스코어링이 완료되어 내림차순 정렬된 추천 코인 리스트
     */
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
                    log.debug("[PortfolioScoringEngine] 밈 코인 필터링 제거됨 - market: {}", dto.market());
                    continue;
                } else if (memeMaxWeight == 30) {
                    score += 10; // ACTIVE면 밈 코인에 약간의 가산점
                }
            }

            // --- Q3: Crypto Themes (백엔드 테마 가중치) ---
            if (dto.theme() != null && userThemes.contains(dto.theme())) {
                score += 200; // 유저의 관심 테마와 일치하면 압도적 가산점
                log.debug("[PortfolioScoringEngine] 테마 일치 가산점 200 부여 - market: {}, theme: {}", dto.market(), dto.theme());
            }

            // --- Q2: Trend Sensitivity (트렌드 추종) ---
            if (trendScore >= 8) { // FULL_TREND
                if (changeRate >= 0.05) {
                    score += 50; // 당일 급등 시 탑승
                    log.debug("[PortfolioScoringEngine] 풀 트렌드 추종 가산점 50 부여 - market: {}, changeRate: {}", dto.market(), changeRate * 100);
                }
            } else if (trendScore <= 3) { // FUNDAMENTAL_ONLY
                if (changeRate >= 0.05) {
                    score -= 50; // 급등주 FOMO 회피
                    log.debug("[PortfolioScoringEngine] 급등주 감점 50 부여 - market: {}, changeRate: {}", dto.market(), changeRate * 100);
                }
            }

            // --- Q1: Risk Tolerance (하락장 방어) ---
            if (riskScore <= 3) { // SELL_IMMEDIATELY
                if (changeRate <= -0.05) {
                    drop = true; // -5% 이상 하락 코인은 즉각 배제
                    log.debug("[PortfolioScoringEngine] 즉각 매도 성향으로 인한 하락 코인 제거 - market: {}, changeRate: {}", dto.market(), changeRate * 100);
                }
            } else if (riskScore >= 8) { // BUY_MORE
                if (changeRate <= -0.1) {
                    score += 50; // -10% 이상 하락 시 줍줍 가산점
                    log.debug("[PortfolioScoringEngine] 추가 매수 성향으로 인한 하락 코인 가산점 50 부여 - market: {}, changeRate: {}", dto.market(), changeRate * 100);
                }
            }

            // --- Q4: Diversification (시간에 따른 포트폴리오 분산도) ---
            if (pastPurchasedMarkets != null && !pastPurchasedMarkets.isEmpty()) {
                boolean isAlreadyOwned = pastPurchasedMarkets.contains(dto.market());
                
                if (divType == DiversificationType.CONCENTRATED) {
                    if (isAlreadyOwned) {
                        score += 100; // 기존에 샀던 코인에 대폭 가산 (한 우물 파기)
                        log.debug("[PortfolioScoringEngine] 집중 투자 성향으로 인한 기보유 코인 가산점 100 부여 - market: {}", dto.market());
                    }
                } else if (divType == DiversificationType.DIVERSIFIED) {
                    if (isAlreadyOwned) {
                        score -= 100; // 기존에 산 코인은 감점 (새로운 코인 순환 매수 유도)
                        log.debug("[PortfolioScoringEngine] 분산 투자 성향으로 인한 기보유 코인 감점 100 부여 - market: {}", dto.market());
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