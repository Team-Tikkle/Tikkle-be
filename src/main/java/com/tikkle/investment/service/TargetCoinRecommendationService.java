package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.AiCandidateResponse;
import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.investment.repository.InvestmentProfileRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.client.UpbitTickerClient;
import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.tikkle.investment.exception.CoinNotFoundException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Collectors;

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
                                log.info("실시간 퀀트 스코어링 완료! 상위 3개 후보군: {}",
                                        finalTargets.stream().limit(3).map(dto -> dto.coinName() + "(" + dto.market() + ")").toList());

                                targetMarket = finalTargets.get(0).market();
                                targetCoinName = finalTargets.get(0).coinName();
                                log.info("최종 매수 타겟 코인 확정: {} ({})", targetCoinName, targetMarket);
                            } else {
                                log.warn("퀀트 스코어링 후 유효한 후보군이 없습니다. Fallback으로 기본 코인(BTC)을 매수합니다.");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("AI 후보군 Redis 파싱 및 실시간 퀀트 스코어링 실패. Fallback으로 BTC를 매수합니다.", e);
                }
            }
        }

        Coin targetCoin = coinRepository.findById(targetMarket)
                .orElseThrow(CoinNotFoundException::new);

        return new CoinRecommendation(targetMarket, targetCoinName, targetCoin);
    }
}