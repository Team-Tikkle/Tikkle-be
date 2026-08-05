package com.tikkle.investment.service;

import com.tikkle.investment.client.AlternativeClient;
import com.tikkle.investment.client.CoinGeckoClient;
import com.tikkle.investment.client.ForexFactoryClient;
import com.tikkle.investment.dto.response.AiCandidateResponse;
import com.tikkle.investment.dto.response.AiRecommendationDto;
import com.tikkle.investment.entity.AiRecommendationHistory;
import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.entity.InvestmentProfile;
import com.tikkle.investment.entity.enums.RiskTolerance;
import com.tikkle.investment.entity.enums.TrendSensitivity;
import com.tikkle.investment.exception.AiRecommendationFailedException;
import com.tikkle.investment.repository.AiRecommendationHistoryRepository;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.upbit.client.UpbitCandleClient;
import com.tikkle.upbit.client.UpbitTickerClient;
import com.tikkle.upbit.dto.response.UpbitCandleResponse;
import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 12시간마다 동작하여 RAG(검색 증강) 방식으로 거시경제 지표와 Top 50 활성 코인 리스트를 
 * LLM에게 주입하여 1차 후보군 풀(15개 코인)을 도출하고 Redis에 캐싱하는 AI 포트폴리오 서비스입니다.
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
    private final CoinRepository coinRepository;
    private final UpbitTickerClient upbitTickerClient;

    public AiPortfolioService(
            @Qualifier("openAiChatModel") ChatModel openAiChatModel,
            AlternativeClient alternativeClient,
            CoinGeckoClient coinGeckoClient,
            UpbitCandleClient upbitCandleClient,
            StringRedisTemplate redisTemplate,
            JsonMapper objectMapper,
            AiRecommendationHistoryRepository historyRepository,
            ForexFactoryClient forexFactoryClient,
            CoinRepository coinRepository,
            UpbitTickerClient upbitTickerClient) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        this.alternativeClient = alternativeClient;
        this.coinGeckoClient = coinGeckoClient;
        this.upbitCandleClient = upbitCandleClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.historyRepository = historyRepository;
        this.forexFactoryClient = forexFactoryClient;
        this.coinRepository = coinRepository;
        this.upbitTickerClient = upbitTickerClient;
    }

    public void generateMacroUniverses() {
        log.info("[AiPortfolioService] AI 매크로 유니버스 생성 시작 (Stage 1)");
        
        // 1. Context Data 수집
        String fngIndex = alternativeClient.getFearAndGreedIndex();
        logFetchResult("Fear & Greed Index", fngIndex);

        String btcDom = coinGeckoClient.getBtcDominance();
        logFetchResult("BTC Dominance", btcDom);

        String hotNarratives = coinGeckoClient.getTopHotNarratives();
        logFetchResult("Hot Narratives", hotNarratives);

        String macroEvents = forexFactoryClient.getUpcomingMacroEvents();
        logFetchResult("Macro Events", macroEvents);
        
        List<UpbitCandleResponse> btcCandles = upbitCandleClient.getWeeklyCandles("KRW-BTC", 1);
        log.info("[AiPortfolioService] 외부 API 수집 {} - BTC 주봉 캔들: {}건 조회", btcCandles.isEmpty() ? "실패" : "성공", btcCandles.size());

        List<UpbitCandleResponse> ethCandles = upbitCandleClient.getWeeklyCandles("KRW-ETH", 1);
        log.info("[AiPortfolioService] 외부 API 수집 {} - ETH 주봉 캔들: {}건 조회", ethCandles.isEmpty() ? "실패" : "성공", ethCandles.size());
        
        String btcWeekly = btcCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", btcCandles.get(0).getChangeRate() * 100);
        String ethWeekly = ethCandles.isEmpty() ? "Unknown" : String.format("%.2f%%", ethCandles.get(0).getChangeRate() * 100);
        String weeklyTrend = String.format("BTC 1W Change: %s, ETH 1W Change: %s", btcWeekly, ethWeekly);
        
        // 2. RAG Context: Top 50 활성 코인 추출 (9개 조합이 공유하므로 1회만 조회)
        String top50CoinsContext = null;
        try {
            top50CoinsContext = getTop50ActiveCoinsContext();
        } catch (Exception e) {
            log.error("[AiPortfolioService] RAG 용 Top 50 코인 리스트 수집 실패", e);
        }

        if (top50CoinsContext == null || top50CoinsContext.isBlank()) {
            top50CoinsContext = null;
            log.error("[AiPortfolioService] RAG 용 Top 50 코인 리스트를 확보하지 못해 전체 조합을 Fallback 처리합니다");
        } else {
            log.info("[AiPortfolioService] 외부 데이터 및 RAG 용 Top 50 코인 리스트 수집 완료");
        }

        int successCount = 0;
        int failCount = 0;

        for (RiskTolerance risk : RiskTolerance.values()) {
            for (TrendSensitivity trend : TrendSensitivity.values()) {
                String hashKey = risk.name() + ":" + trend.name();

                // 컨텍스트가 없으면 AI를 호출하지 않는다 (환각 응답이 캐시에 적재되는 것을 막는다)
                if (top50CoinsContext == null) {
                    failCount++;
                    loadFallbackCandidates(hashKey);
                    continue;
                }

                try {
                    List<AiRecommendationDto> aiCandidates = fetchAiMacroCandidates(
                            risk, trend, fngIndex, btcDom, weeklyTrend, hotNarratives, macroEvents, top50CoinsContext);
                    
                    String redisKey = "ai:candidates:" + hashKey;
                    String jsonValue = objectMapper.writeValueAsString(new AiCandidateResponse(aiCandidates));
                    redisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofHours(24));
                    log.info("[AiPortfolioService] 레디스에 15개 후보군 캐싱 완료 - hashKey: {}", hashKey);

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
                    
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("[AiPortfolioService] 매크로 후보군 조회 실패 - hashKey: {}", hashKey, e);
                    loadFallbackCandidates(hashKey);
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
     * 후보군 생성에 실패한 조합에 대해 DB의 직전 추천 내역을 Redis에 다시 적재합니다.
     */
    private void loadFallbackCandidates(String hashKey) {
        historyRepository.findTopByProfileHashKeyOrderByIdDesc(hashKey)
                .ifPresentOrElse(
                        history -> {
                            log.warn("[AiPortfolioService] DB에서 이전 추천 데이터(Fallback)를 불러옵니다. hashKey: {}", hashKey);
                            String redisKey = "ai:candidates:" + hashKey;
                            redisTemplate.opsForValue().set(redisKey, history.getCandidatesJson(), Duration.ofHours(24));
                        },
                        () -> log.error("[AiPortfolioService] DB에 이전 데이터가 존재하지 않아 Fallback 불가. hashKey: {}", hashKey)
                );
    }

    /**
     * 거래대금 기준 상위 50개의 코인 리스트를 생성하여 AI에게 RAG 컨텍스트로 제공합니다.
     */
    private String getTop50ActiveCoinsContext() {
        List<Coin> allCoins = coinRepository.findAllByIsActiveTrue();
        if (allCoins.isEmpty()) {
            // 화이트리스트가 빈 채로 프롬프트에 들어가면 AI가 없는 티커를 만들어내므로 컨텍스트 없음으로 처리한다
            log.error("[AiPortfolioService] 활성 코인이 DB에 없어 RAG 컨텍스트를 생성할 수 없습니다");
            return null;
        }

        String markets = allCoins.stream()
                .map(Coin::getMarket)
                .collect(Collectors.joining(","));

        List<UpbitTickerResponse> tickers = upbitTickerClient.getTickers(markets);

        List<UpbitTickerResponse> top50Tickers = tickers.stream()
                .filter(t -> t.accTradePrice24h() != null)
                .sorted((a, b) -> b.accTradePrice24h().compareTo(a.accTradePrice24h()))
                .limit(50)
                .toList();

        if (top50Tickers.isEmpty()) {
            log.error("[AiPortfolioService] 업비트 시세 조회 결과가 비어 RAG 컨텍스트를 생성할 수 없습니다 - activeCoins: {}건", allCoins.size());
            return null;
        }

        Map<String, String> coinNames = allCoins.stream().collect(Collectors.toMap(Coin::getMarket, Coin::getKoreanName));
        
        StringBuilder sb = new StringBuilder();
        for (UpbitTickerResponse t : top50Tickers) {
            String name = coinNames.getOrDefault(t.market(), "Unknown");
            sb.append("- ").append(t.market()).append(" (").append(name).append(")\n");
        }
        return sb.toString();
    }

    private List<AiRecommendationDto> fetchAiMacroCandidates(RiskTolerance risk, TrendSensitivity trend, 
            String fngIndex, String btcDom, String weeklyTrend, String hotNarratives, String macroEvents, String top50CoinsContext) {
        
        String promptText = """
            You are the Lead Quantitative Strategist at a top-tier crypto hedge fund.
            Your task is to generate a highly optimized candidate pool of EXACTLY 15 cryptocurrencies.
            This pool will be passed to our backend quant engine for real-time scoring. 
            You must deeply analyze the current macro market context and strictly adhere to the user's investment profile.

            [Market Context]
            - Fear & Greed Index: {fngIndex} (0-100)
            - BTC Dominance: {btcDom}%
            - 7-Day Macro Market Trend: {weeklyTrend}
            - Current Hot Narratives: {hotNarratives}
            - Upcoming Macro Events (Next 48H): {macroEvents}

            [User Profile (Target Audience for this Pool)]
            - Risk Tolerance (Reaction to crashes): {riskTolerance}
            - Trend Sensitivity (Reaction to hype): {trendSensitivity}

            [CRITICAL RAG CONTEXT: TOP 50 ACTIVE COINS]
            You MUST select the 15 coins ONLY from the following list of Top 50 most actively traded coins right now:
            {top50CoinsContext}
            NEVER hallucinate or recommend a coin outside of this exact list!

            [Strategic Directives & Constraints]
            1. QUANTITY & SOURCE: You MUST select EXACTLY 15 unique coins STRICTLY from the [TOP 50 ACTIVE COINS] list above. Do not hallucinate tickers.
            2. THEME DIVERSITY: To prevent Pool Starvation in our backend engine, your 15 selections MUST span across major themes. You must deduce the theme of each coin yourself.
               - Try to include coins from these themes: LAYER_1, DEFI, AI, WEB3_GAMING, RWA, MEME.
               - CRITICAL FALLBACK: If a specific theme (e.g., RWA or AI) does not exist in the provided [TOP 50 ACTIVE COINS] list, DO NOT hallucinate. Simply allocate those slots to other available themes.
            3. RISK MANAGEMENT: 
               - If Upcoming Macro Events contain high-impact volatility triggers OR Fear & Greed is fearful, and the user's Risk Tolerance is conservative ('SELL_IMMEDIATELY'), prioritize heavy blue-chips from the list.
               - If the user is aggressive ('BUY_MORE'), include high-beta altcoins.
            4. OUTPUT FORMAT: Return ONLY valid JSON representing an object with a 'candidates' array. No conversational text or markdown blocks.
            5. SCHEMA per candidate: 
               - 'market' (String, MUST be the exact ticker from the list, e.g., 'KRW-BTC')
               - 'coinName' (String, MUST be written in English, e.g., "Bitcoin")
               - 'reason' (String, MUST be under 4 words, very crisp reasoning)
               - 'theme' (String, MUST exactly match one of: LAYER_1, DEFI, AI, WEB3_GAMING, RWA, MEME)
               - 'isMeme' (Boolean, true if theme is MEME)
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
                                .param("top50CoinsContext", top50CoinsContext)
                        )
                        .stream()
                        .content()
                        .reduce("", String::concat)
                        .block();
                
                if (responseText != null) {
                    int startIndex = responseText.indexOf('{');
                    int endIndex = responseText.lastIndexOf('}');
                    if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                        responseText = responseText.substring(startIndex, endIndex + 1);
                    } else {
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

    public String generateProfileHashKey(InvestmentProfile profile) {
        String risk = profile.getRiskTolerance().name();
        String trend = profile.getTrendSensitivity().name();
        return String.join(":", risk, trend);
    }

    private void logFetchResult(String dataName, String dataValue) {
        if (dataValue == null || dataValue.startsWith("Unknown")) {
            log.warn("[AiPortfolioService] 외부 API 수집 실패 - {}: {}", dataName, dataValue);
        } else {
            log.info("[AiPortfolioService] 외부 API 수집 성공 - {}: {}", dataName, dataValue);
        }
    }
}