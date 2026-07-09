package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiCandidateResponse;
import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.exception.CoinNotFoundException;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.investment.entity.AiRecommendationHistory;
import com.tikkle.investment.repository.AiRecommendationHistoryRepository;
import com.tikkle.upbit.client.UpbitTickerClient;
import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Collectors;
import java.time.Duration;

/**
 * AI가 생성해둔 후보군 캐시를 조회하여, 퀀트 엔진을 거친 뒤 사용자가 잔돈으로 매수할
 * 최종 타겟 코인 1개를 결정하는 오케스트레이션 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetCoinRecommendationService {

    private final InvestmentProfileRepository investmentProfileRepository;
    private final AiPortfolioService aiPortfolioService;
    private final PortfolioScoringEngine portfolioScoringEngine;
    private final UpbitTickerClient upbitTickerClient;
    private final CoinRepository coinRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AiRecommendationHistoryRepository aiRecommendationHistoryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final JsonMapper objectMapper;

    public record CoinRecommendation(String market, String coinName, Coin coin) {}

    private static final String DEFAULT_FALLBACK_MARKET = "KRW-BTC";
    private static final String DEFAULT_FALLBACK_COIN_NAME = "비트코인";

    /**
     * AI 후보군 + 퀀트 스코어링으로 매수 타겟 코인을 결정한다.
     * 실패 시 기본 코인(BTC)으로 폴백.
     */
    public CoinRecommendation recommendCoin(Long userId) {
        String targetMarket = DEFAULT_FALLBACK_MARKET;
        String targetCoinName = DEFAULT_FALLBACK_COIN_NAME;

        InvestmentProfile profile = investmentProfileRepository.findByUserId(userId).orElse(null);

        if (profile != null) {
            String hashKey = aiPortfolioService.generateProfileHashKey(profile);
            String redisKey = "ai:candidates:" + hashKey;
            String jsonCandidates = redisTemplate.opsForValue().get(redisKey);

            if (jsonCandidates == null) {
                log.warn("[TargetCoinRecommendationService] Redis 캐시 미스 - DB에서 최근 내역(Fallback) 조회 시도 (hashKey: {})", hashKey);
                AiRecommendationHistory history = aiRecommendationHistoryRepository.findTopByProfileHashKeyOrderByIdDesc(hashKey).orElse(null);
                if (history != null) {
                    jsonCandidates = history.getCandidatesJson();
                    redisTemplate.opsForValue().set(redisKey, jsonCandidates, Duration.ofHours(12));
                    log.info("[TargetCoinRecommendationService] DB 조회 성공 및 Redis 적재 완료 (hashKey: {})", hashKey);
                }
            }

            if (jsonCandidates != null) {
                try {
                    AiCandidateResponse candidateResponse = objectMapper.readValue(jsonCandidates, AiCandidateResponse.class);
                    List<AiRecommendationDto> macroCandidates = candidateResponse.candidates();

                    if (macroCandidates != null && !macroCandidates.isEmpty()) {
                        List<String> validMarkets = coinRepository.findAll().stream().map(Coin::getMarket).toList();

                        List<AiRecommendationDto> validCandidates = macroCandidates.stream()
                                .filter(dto -> validMarkets.contains(dto.market()))
                                .toList();

                        if (!validCandidates.isEmpty()) {
                            String marketsParam = validCandidates.stream()
                                    .map(AiRecommendationDto::market)
                                    .collect(Collectors.joining(","));

                            List<UpbitTickerResponse> tickers = upbitTickerClient.getTickers(marketsParam);

                            List<String> pastPurchasedMarkets = paymentEventRepository.findRecentPurchasedMarkets(
                                    userId,
                                    PageRequest.of(0, 10)
                            );

                            List<AiRecommendationDto> finalTargets = portfolioScoringEngine.selectRealtimeTargets(
                                    validCandidates, tickers, profile, pastPurchasedMarkets
                            );

                            if (!finalTargets.isEmpty()) {
                                log.info("[TargetCoinRecommendationService] 실시간 퀀트 스코어링 완료! 상위 3개 후보군: {}",
                                        finalTargets.stream().limit(3).map(dto -> dto.coinName() + "(" + dto.market() + ")").toList());

                                targetMarket = finalTargets.get(0).market();
                                targetCoinName = finalTargets.get(0).coinName();
                                log.info("[TargetCoinRecommendationService] 최종 매수 타겟 코인 확정: {} ({})", targetCoinName, targetMarket);
                            } else {
                                log.warn("[TargetCoinRecommendationService] 퀀트 스코어링 후 유효한 후보군이 없습니다. Fallback으로 기본 코인(BTC)을 매수합니다.");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[TargetCoinRecommendationService] AI 후보군 Redis 파싱 및 실시간 퀀트 스코어링 실패. Fallback으로 BTC를 매수합니다 - errorMessage: {}", e.getMessage(), e);
                }
            }
        }

        Coin targetCoin = coinRepository.findById(targetMarket)
                .orElseThrow(CoinNotFoundException::new);

        return new CoinRecommendation(targetMarket, targetCoinName, targetCoin);
    }
}