package com.tikkle.investment.service;

import com.tikkle.investment.client.CoinGeckoClient;
import com.tikkle.investment.client.FearAndGreedClient;
import com.tikkle.investment.dto.response.AiCandidateResponse;
import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.AiRecommendationHistory;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.entity.enums.RiskTolerance;
import com.tikkle.investment.entity.enums.TrendSensitivity;
import com.tikkle.investment.exception.AiRecommendationFailedException;
import com.tikkle.investment.repository.AiRecommendationHistoryRepository;
import com.tikkle.upbit.client.UpbitCandleClient;
import com.tikkle.upbit.dto.response.UpbitCandleResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * 12시간마다 동작하여 거시경제 지표와 사용자의 성향을 조합해 LLM에게 전달하고,
 * 1차 후보군 풀(15개 코인)을 생성하여 Redis에 캐싱하는 AI 포트폴리오 서비스입니다.
 */
@Slf4j
@Service
public class AiPortfolioService {
    private final ChatClient chatClient;
    private final FearAndGreedClient fearAndGreedClient;
    private final CoinGeckoClient coinGeckoClient;
    private final UpbitCandleClient upbitCandleClient;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper objectMapper;
    private final AiRecommendationHistoryRepository historyRepository;

    public AiPortfolioService(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
            FearAndGreedClient fearAndGreedClient,
            CoinGeckoClient coinGeckoClient,
            UpbitCandleClient upbitCandleClient,
            StringRedisTemplate redisTemplate,
            JsonMapper objectMapper,
            AiRecommendationHistoryRepository historyRepository) {
        this.chatClient = ChatClient.builder(anthropicChatModel).build();
        this.fearAndGreedClient = fearAndGreedClient;
        this.coinGeckoClient = coinGeckoClient;
        this.upbitCandleClient = upbitCandleClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.historyRepository = historyRepository;
    }

    /**
     * 외부 매크로 데이터(F&G 인덱스, BTC 도미넌스, 주간 변동률)를 조회하고,
     * 위험 감수성(Risk)과 트렌드 민감도(Trend)의 9가지 조합별로 
     * AI 모델을 호출하여 각각 15개의 후보군을 도출한 후 Redis에 저장합니다.
     */
    public void generateMacroUniverses() {
        log.info("[AiPortfolioService] AI 매크로 유니버스 생성 시작 (Stage 1)");
        
        // 1. Context Data 수집
        String fngIndex = fearAndGreedClient.getFearAndGreedIndex();
        String btcDom = coinGeckoClient.getBtcDominance();
        
        // 주간 추세 데이터 수집 (BTC, ETH)
        List<UpbitCandleResponse> btcCandles = upbitCandleClient.getWeeklyCandles("KRW-BTC", 1);
        List<UpbitCandleResponse> ethCandles = upbitCandleClient.getWeeklyCandles("KRW-ETH", 1);
        
        String btcWeekly = btcCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", btcCandles.get(0).getChangeRate() * 100);
        String ethWeekly = ethCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", ethCandles.get(0).getChangeRate() * 100);
        String weeklyTrend = String.format("BTC 1W Change: %s, ETH 1W Change: %s", btcWeekly, ethWeekly);
        
        log.info("[AiPortfolioService] 외부 데이터 조회 완료 - fngIndex: {}, btcDominance: {}%, weeklyTrend: {}", fngIndex, btcDom, weeklyTrend);
        
        int successCount = 0;
        int failCount = 0;

        // 모든 RiskTolerance와 TrendSensitivity의 조합 (최대 3x3 = 9개)을 생성합니다.
        for (RiskTolerance risk : RiskTolerance.values()) {
            for (TrendSensitivity trend : TrendSensitivity.values()) {
                String hashKey = risk.name() + ":" + trend.name();
                try {
                    List<AiRecommendationDto> aiCandidates = fetchAiMacroCandidates(risk, trend, fngIndex, btcDom, weeklyTrend);
                    
                    // Redis에 12시간 TTL로 캐싱
                    String redisKey = "ai:candidates:" + hashKey;
                    String jsonValue = objectMapper.writeValueAsString(new AiCandidateResponse(aiCandidates));
                    redisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofHours(12));
                    log.info("[AiPortfolioService] 레디스에 15개 후보군 캐싱 완료 - hashKey: {}", hashKey);

                    // DB에 히스토리 저장 (Analytics 및 백테스팅 용도)
                    AiRecommendationHistory history = AiRecommendationHistory.builder()
                            .profileHashKey(hashKey)
                            .fngIndex(fngIndex)
                            .btcDominance(btcDom)
                            .weeklyTrend(weeklyTrend)
                            .candidatesJson(jsonValue)
                            .build();
                    historyRepository.save(history);
                    log.info("[AiPortfolioService] DB에 AI 추천 히스토리 저장 완료 - hashKey: {}", hashKey);
                    
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("[AiPortfolioService] 매크로 후보군 조회 및 캐싱 실패 - hashKey: {}", hashKey, e);
                }
            }
        }
        
        if (failCount == 0) {
            log.info("[AiPortfolioService] 9개 조합에 대한 AI 매크로 유니버스 생성 전체 완료 (성공: {}건)", successCount);
        } else {
            log.warn("[AiPortfolioService] AI 매크로 유니버스 생성 부분 실패 (성공: {}건, 실패: {}건)", successCount, failCount);
        }
    }

    /**
     * AI 챗 모델(Anthropic)에 프롬프트를 전송하여 시장 상황에 맞는 가상자산 15종을 추천받습니다.
     *
     * @param risk 사용자 위험 감수성
     * @param trend 사용자 트렌드 민감도
     * @param fngIndex 공포 탐욕 지수
     * @param btcDom 비트코인 도미넌스
     * @param weeklyTrend 주간 비트코인/이더리움 변동 추세
     * @return 추천된 코인 후보군 리스트 (정확히 15개)
     */
    private List<AiRecommendationDto> fetchAiMacroCandidates(RiskTolerance risk, TrendSensitivity trend, String fngIndex, String btcDom, String weeklyTrend) {
        String promptText = """
            You are a top-tier quantitative crypto portfolio manager. 
            Based on the user's Risk Tolerance, Trend Sensitivity, and current macro market context, output a 'Broad Candidate Pool' of 30 to 40 cryptocurrencies from the Upbit KRW market.
            This broad pool will later be dynamically filtered and scored by a backend quant engine based on the user's specific theme preferences, meme coin acceptance, and real-time volatility.

            [Market Context]
            - Fear & Greed Index: {fngIndex} (0-100)
            - BTC Dominance: {btcDom}%
            - 7-Day Macro Market Trend: {weeklyTrend}

            [User Profile (Pool Constraints)]
            - Risk Tolerance (Reaction to crashes): {riskTolerance}
            - Trend Sensitivity (Reaction to hype): {trendSensitivity}

            [Constraints & Rules]
            1. Select EXACTLY 15 coins that fit the Risk Tolerance and Trend Sensitivity in the current macro market. DO NOT exceed 15 coins.
            2. If Risk Tolerance is 'SELL_IMMEDIATELY' and the market is fearful, ensure heavy blue-chips (BTC/ETH) are prominent. If 'BUY_MORE', include fundamentally strong altcoins that have dropped in price.
            3. If Trend Sensitivity is 'FUNDAMENTAL_ONLY', avoid hype/trend coins. If 'FULL_TREND', aggressively include current narrative hot coins (e.g., AI, Memes).
            4. CRITICAL: Your pool must be extremely diverse and cover ALL major themes so the backend has enough variety to filter. Ensure you include coins from: LAYER_1, DEFI, AI, WEB3_GAMING, RWA, and MEME.
            5. Output the result in valid JSON containing an array of 'candidates'.
            6. Each candidate MUST contain: 'market' (MUST be exactly in 'KRW-XXX' format, e.g., 'KRW-BTC', 'KRW-SOL'), 'coinName', 'reason' (keep reason VERY SHORT, max 20 chars), 'theme' (must be exactly one of LAYER_1, DEFI, AI, WEB3_GAMING, RWA, MEME), and 'isMeme' (boolean true/false).
            7. DO NOT add any conversational text outside the JSON.
            """;

        try {
            String responseText = chatClient.prompt()
                    .user(u -> u.text(promptText)
                            .param("fngIndex", fngIndex)
                            .param("btcDom", btcDom)
                            .param("weeklyTrend", weeklyTrend)
                            .param("riskTolerance", risk.name())
                            .param("trendSensitivity", trend.name())
                    )
                    .call()
                    .content();
            
            // LLM이 흔히 붙이는 마크다운 백틱(```json) 제거
            if (responseText != null) {
                responseText = responseText.trim();
                if (responseText.startsWith("```json")) {
                    responseText = responseText.substring(7);
                } else if (responseText.startsWith("```")) {
                    responseText = responseText.substring(3);
                }
                if (responseText.endsWith("```")) {
                    responseText = responseText.substring(0, responseText.length() - 3);
                }
                responseText = responseText.trim();
            }

            AiCandidateResponse response = objectMapper.readValue(responseText, AiCandidateResponse.class);
                    
            log.info("[AiPortfolioService] AI 후보군 생성 완료 - candidatesSize: {}, risk: {}, trend: {}", 
                    response.candidates().size(), risk.name(), trend.name());
            return response.candidates();
        } catch (Exception e) {
            log.error("[AiPortfolioService] AI 추천 API 호출 실패", e);
            throw new AiRecommendationFailedException();
        }
    }

    /**
     * 사용자 투자 성향 프로필을 기반으로 AI 후보군 캐시 조회를 위한 해시 키를 생성합니다.
     *
     * @param profile 사용자 투자 성향 프로필
     * @return Risk:Trend 조합의 해시 키
     */
    public String generateProfileHashKey(InvestmentProfile profile) {
        String risk = profile.getRiskTolerance().name();
        String trend = profile.getTrendSensitivity().name();

        // 최적화 (The Ultimate Architecture): 오직 Risk와 Trend 조합만 사용하여 유니버스 그룹화 (최대 3x3 = 9개 조합)
        // Theme과 Meme은 백엔드 스코어링 엔진(Stage 2)에서 실시간 처리됨.
        return String.join(":", risk, trend);
    }
}