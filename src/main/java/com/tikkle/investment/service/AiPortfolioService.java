package com.tikkle.investment.service;

import com.tikkle.investment.client.CoinGeckoClient;
import com.tikkle.investment.client.FearAndGreedClient;
import com.tikkle.investment.dto.response.AiCandidateResponse;
import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.AiRecommendationHistory;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.exception.AiRecommendationFailedException;
import com.tikkle.investment.repository.AiRecommendationHistoryRepository;
import com.tikkle.investment.repository.InvestmentProfileRepository;
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
import com.tikkle.investment.entity.enums.RiskTolerance;
import com.tikkle.investment.entity.enums.TrendSensitivity;

@Slf4j
@Service
public class AiPortfolioService {
    private final ChatClient chatClient;
    private final InvestmentProfileRepository investmentProfileRepository;
    private final FearAndGreedClient fearAndGreedClient;
    private final CoinGeckoClient coinGeckoClient;
    private final UpbitCandleClient upbitCandleClient;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper objectMapper;
    private final AiRecommendationHistoryRepository historyRepository;

    public AiPortfolioService(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
            InvestmentProfileRepository investmentProfileRepository,
            FearAndGreedClient fearAndGreedClient,
            CoinGeckoClient coinGeckoClient,
            UpbitCandleClient upbitCandleClient,
            StringRedisTemplate redisTemplate,
            JsonMapper objectMapper,
            AiRecommendationHistoryRepository historyRepository) {
        this.chatClient = ChatClient.builder(anthropicChatModel).build();
        this.investmentProfileRepository = investmentProfileRepository;
        this.fearAndGreedClient = fearAndGreedClient;
        this.coinGeckoClient = coinGeckoClient;
        this.upbitCandleClient = upbitCandleClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.historyRepository = historyRepository;
    }

    public void generateMacroUniverses() {
        log.info("Starting AI Macro Universe generation (Stage 1).");
        
        // 1. Context Data 수집
        String fngIndex = fearAndGreedClient.getFearAndGreedIndex();
        String btcDom = coinGeckoClient.getBtcDominance();
        
        // 주간 추세 데이터 수집 (BTC, ETH)
        List<UpbitCandleResponse> btcCandles = upbitCandleClient.getWeeklyCandles("KRW-BTC", 1);
        List<UpbitCandleResponse> ethCandles = upbitCandleClient.getWeeklyCandles("KRW-ETH", 1);
        
        String btcWeekly = btcCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", btcCandles.get(0).getChangeRate() * 100);
        String ethWeekly = ethCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", ethCandles.get(0).getChangeRate() * 100);
        String weeklyTrend = String.format("BTC 1W Change: %s, ETH 1W Change: %s", btcWeekly, ethWeekly);
        
        log.info("[Macro Context] Fetched External Data -> F&G Index: {}, BTC Dominance: {}%, Weekly Trend: {}", fngIndex, btcDom, weeklyTrend);
        
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
                    log.info("Successfully cached 15 candidates in Redis for hash: {}", hashKey);

                    // DB에 히스토리 저장 (Analytics 및 백테스팅 용도)
                    AiRecommendationHistory history = AiRecommendationHistory.builder()
                            .profileHashKey(hashKey)
                            .fngIndex(fngIndex)
                            .btcDominance(btcDom)
                            .weeklyTrend(weeklyTrend)
                            .candidatesJson(jsonValue)
                            .build();
                    historyRepository.save(history);
                    log.info("Successfully saved AI recommendation history to DB for hash: {}", hashKey);
                    
                } catch (Exception e) {
                    log.error("Failed to fetch/cache macro candidates for group: {}", hashKey, e);
                }
            }
        }
        log.info("Finished AI Macro Universe generation for all 9 combinations.");
    }

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
                    
            log.info("AI generated {} candidates for Risk: {}, Trend: {}", 
                    response.candidates().size(), risk.name(), trend.name());
            return response.candidates();
        } catch (Exception e) {
            log.error("AI Recommendation API call failed", e);
            throw new AiRecommendationFailedException();
        }
    }

    public String generateProfileHashKey(InvestmentProfile profile) {
        String risk = profile.getRiskTolerance().name();
        String trend = profile.getTrendSensitivity().name();

        // 최적화 (The Ultimate Architecture): 오직 Risk와 Trend 조합만 사용하여 유니버스 그룹화 (최대 3x3 = 9개 조합)
        // Theme과 Meme은 백엔드 스코어링 엔진(Stage 2)에서 실시간 처리됨.
        return String.join(":", risk, trend);
    }
}