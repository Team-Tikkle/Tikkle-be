package com.tikkle.investment.service;

import com.tikkle.investment.client.AlternativeClient;
import com.tikkle.investment.client.CoinGeckoClient;
import com.tikkle.investment.client.ForexFactoryClient;
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
    private final AlternativeClient alternativeClient;
    private final CoinGeckoClient coinGeckoClient;
    private final UpbitCandleClient upbitCandleClient;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper objectMapper;
    private final AiRecommendationHistoryRepository historyRepository;
    private final ForexFactoryClient forexFactoryClient;

    public AiPortfolioService(
            @Qualifier("openAiChatModel") ChatModel openAiChatModel,
            AlternativeClient alternativeClient,
            CoinGeckoClient coinGeckoClient,
            UpbitCandleClient upbitCandleClient,
            StringRedisTemplate redisTemplate,
            JsonMapper objectMapper,
            AiRecommendationHistoryRepository historyRepository,
            ForexFactoryClient forexFactoryClient) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        this.alternativeClient = alternativeClient;
        this.coinGeckoClient = coinGeckoClient;
        this.upbitCandleClient = upbitCandleClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.historyRepository = historyRepository;
        this.forexFactoryClient = forexFactoryClient;
    }

    /**
     * 외부 매크로 데이터(F&G 인덱스, BTC 도미넌스, 주간 변동률)를 조회하고,
     * 위험 감수성(Risk)과 트렌드 민감도(Trend)의 9가지 조합별로 
     * AI 모델을 호출하여 각각 15개의 후보군을 도출한 후 Redis에 저장합니다.
     */
    public void generateMacroUniverses() {
        log.info("[AiPortfolioService] AI 매크로 유니버스 생성 시작 (Stage 1)");
        
        // 1. Context Data 수집
        String fngIndex = alternativeClient.getFearAndGreedIndex();
        String btcDom = coinGeckoClient.getBtcDominance();
        String hotNarratives = coinGeckoClient.getTopHotNarratives();
        String macroEvents = forexFactoryClient.getUpcomingMacroEvents();
        
        // 주간 추세 데이터 수집 (BTC, ETH)
        List<UpbitCandleResponse> btcCandles = upbitCandleClient.getWeeklyCandles("KRW-BTC", 1);
        List<UpbitCandleResponse> ethCandles = upbitCandleClient.getWeeklyCandles("KRW-ETH", 1);
        
        String btcWeekly = btcCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", btcCandles.get(0).getChangeRate() * 100);
        String ethWeekly = ethCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", ethCandles.get(0).getChangeRate() * 100);
        String weeklyTrend = String.format("BTC 1W Change: %s, ETH 1W Change: %s", btcWeekly, ethWeekly);
        
        log.info("[AiPortfolioService] 외부 데이터 조회 완료 - fngIndex: {}, btcDominance: {}%, weeklyTrend: {}, hotNarratives: {}, macroEvents: {}", fngIndex, btcDom, weeklyTrend, hotNarratives, macroEvents);
        
        int successCount = 0;
        int failCount = 0;

        // 모든 RiskTolerance와 TrendSensitivity의 조합 (최대 3x3 = 9개)을 생성합니다.
        for (RiskTolerance risk : RiskTolerance.values()) {
            for (TrendSensitivity trend : TrendSensitivity.values()) {
                String hashKey = risk.name() + ":" + trend.name();
                try {
                    List<AiRecommendationDto> aiCandidates = fetchAiMacroCandidates(risk, trend, fngIndex, btcDom, weeklyTrend, hotNarratives, macroEvents);
                    
                    // Redis에 24시간 TTL로 캐싱 (기존 12시간에서 24시간으로 연장하여 예외 상황 대비)
                    String redisKey = "ai:candidates:" + hashKey;
                    String jsonValue = objectMapper.writeValueAsString(new AiCandidateResponse(aiCandidates));
                    redisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofHours(24));
                    log.info("[AiPortfolioService] 레디스에 15개 후보군 캐싱 완료 - hashKey: {}", hashKey);

                    // DB에 히스토리 저장 (Analytics 및 백테스팅 용도)
                    AiRecommendationHistory history = AiRecommendationHistory.builder()
                            .profileHashKey(hashKey)
                            .fngIndex(fngIndex)
                            .btcDominance(btcDom)
                            .weeklyTrend(weeklyTrend)
                            .candidatesJson(jsonValue)
                            .hotNarratives(hotNarratives)
                            .macroEvents(macroEvents)
                            .build();
                    historyRepository.save(history);
                    log.info("[AiPortfolioService] DB에 AI 추천 히스토리 저장 완료 - hashKey: {}", hashKey);
                    
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("[AiPortfolioService] 매크로 후보군 조회 및 캐싱 3회 재시도 최종 실패 - hashKey: {}", hashKey, e);
                    
                    // DB Fallback: 가장 최근의 성공 히스토리를 가져와서 레디스에 복구 (Graceful Degradation)
                    historyRepository.findTopByProfileHashKeyOrderByIdDesc(hashKey)
                            .ifPresentOrElse(
                                    history -> {
                                        log.warn("[AiPortfolioService] DB에서 이전 추천 데이터(Fallback)를 불러옵니다. hashKey: {}", hashKey);
                                        String redisKey = "ai:candidates:" + hashKey;
                                        redisTemplate.opsForValue().set(redisKey, history.getCandidatesJson(), Duration.ofHours(24));
                                    },
                                    () -> {
                                        log.error("[AiPortfolioService] DB에 이전 데이터가 존재하지 않아 Fallback 불가. hashKey: {}", hashKey);
                                    }
                            );
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
     * AI 챗 모델(DeepSeek)에 프롬프트를 전송하여 시장 상황에 맞는 가상자산 15종을 추천받습니다.
     *
     * @param risk 사용자 위험 감수성
     * @param trend 사용자 트렌드 민감도
     * @param fngIndex 공포 탐욕 지수
     * @param btcDom 비트코인 도미넌스
     * @param weeklyTrend 주간 비트코인/이더리움 변동 추세
     * @param hotNarratives 현재 주도 테마
     * @param macroEvents 주요 거시경제 일정
     * @return 추천된 코인 후보군 리스트 (정확히 15개)
     */
    private List<AiRecommendationDto> fetchAiMacroCandidates(RiskTolerance risk, TrendSensitivity trend, String fngIndex, String btcDom, String weeklyTrend, String hotNarratives, String macroEvents) {
        String promptText = """
            You are the Lead Quantitative Strategist at a top-tier crypto hedge fund.
            Your task is to generate a highly optimized candidate pool of EXACTLY 15 cryptocurrencies from the Upbit KRW market. 
            This pool will be passed to our backend quant engine for real-time scoring. 
            You must deeply analyze the current macro market context and strictly adhere to the user's investment profile.

            [Market Context]
            - Fear & Greed Index: {fngIndex} (0-100)
            - BTC Dominance: {btcDom}%
            - 7-Day Macro Market Trend: {weeklyTrend}
            - Current Hot Narratives: {hotNarratives}
            - Upcoming Macro Events (Next 48H): {macroEvents}

            [User Profile]
            - Risk Tolerance (Reaction to crashes): {riskTolerance}
            - Trend Sensitivity (Reaction to hype): {trendSensitivity}

            [Strategic Directives & Constraints]
            1. QUANTITY: You MUST select EXACTLY 15 unique coins. Do not output more or less.
            2. MISSING DATA: If any context variable says 'Unknown (Data fetch failed)', completely ignore that metric and do not hallucinate data. Rely on the remaining valid metrics.
            3. RISK MANAGEMENT (Crucial): 
               - If Upcoming Macro Events contain high-impact volatility triggers (e.g., CPI, Fed Speaks) OR Fear & Greed is fearful, and the user's Risk Tolerance is conservative (e.g., 'SELL_IMMEDIATELY'), you MUST prioritize heavy blue-chips (BTC, ETH) and stable Layer-1s. 
               - If the user is aggressive ('BUY_MORE'), you may include high-beta altcoins that offer strong upside bounce opportunities.
            4. NARRATIVE ALIGNMENT: 
               - If Trend Sensitivity is 'FULL_TREND', aggressively include coins that match the 'Current Hot Narratives' and set 'isMeme': true if applicable. 
               - If 'FUNDAMENTAL_ONLY', completely ignore narratives and memes, focusing purely on established infrastructure and DeFi.
            5. DIVERSITY: To ensure the backend engine has enough variety, the 15 coins must span across multiple themes. Use ONLY these exact theme values: LAYER_1, DEFI, AI, WEB3_GAMING, RWA, MEME.
            6. OUTPUT FORMAT: Return ONLY valid JSON representing an object with a 'candidates' array. No conversational text.
            7. SCHEMA per candidate: 
               - 'market' (String, MUST be exactly 'KRW-XXX', e.g., 'KRW-BTC')
               - 'coinName' (String)
               - 'reason' (String, max 30 chars, crisp quant reasoning)
               - 'theme' (String, must match one of the 6 themes above)
               - 'isMeme' (Boolean)
            """;

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String responseText = chatClient.prompt()
                        .user(u -> u.text(promptText)
                                .param("fngIndex", fngIndex)
                                .param("btcDom", btcDom)
                                .param("weeklyTrend", weeklyTrend)
                                .param("hotNarratives", hotNarratives)
                                .param("macroEvents", macroEvents)
                                .param("riskTolerance", risk.name())
                                .param("trendSensitivity", trend.name())
                        )
                        .stream()
                        .content()
                        .reduce("", String::concat)
                        .block();
                
                // 원본 응답 로그 출력 (디버깅 용도)
                log.info("[AiPortfolioService] AI 원본 응답 길이: {}, 내용 미리보기: {}", 
                        responseText != null ? responseText.length() : 0,
                        responseText != null ? responseText.substring(0, Math.min(responseText.length(), 100)).replace("\n", " ") + "..." : "null");

                // LLM의 사족 및 <think> 태그를 무시하고 순수 JSON만 추출
                if (responseText != null) {
                    int startIndex = responseText.indexOf('{');
                    int endIndex = responseText.lastIndexOf('}');
                    if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                        responseText = responseText.substring(startIndex, endIndex + 1);
                    } else {
                        log.error("[AiPortfolioService] AI 응답에서 JSON 객체를 찾을 수 없습니다. 원본: \n{}", responseText);
                        throw new RuntimeException("JSON 형식의 응답이 아닙니다.");
                    }
                }

                AiCandidateResponse response = objectMapper.readValue(responseText, AiCandidateResponse.class);
                        
                log.info("[AiPortfolioService] AI 후보군 생성 완료 - candidatesSize: {}, risk: {}, trend: {}", 
                        response.candidates().size(), risk.name(), trend.name());
                return response.candidates();
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("[AiPortfolioService] AI 추천 API 호출 {}회 모두 실패 - errorMessage: {}", maxRetries, e.getMessage(), e);
                    throw new AiRecommendationFailedException();
                }
                log.warn("[AiPortfolioService] AI 추천 API 호출 실패, 재시도합니다... (시도 횟수: {}/{}) - errorMessage: {}", attempt, maxRetries, e.getMessage());
            }
        }
        throw new AiRecommendationFailedException();
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