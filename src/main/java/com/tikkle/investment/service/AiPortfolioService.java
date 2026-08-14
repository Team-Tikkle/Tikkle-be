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
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 12시간마다 동작하여 RAG(검색 증강) 방식으로 거시경제 지표와 거래대금 상위 활성 코인 리스트를
 * LLM에게 주입하여 1차 후보군 풀(15개 코인)을 도출하고 Redis에 캐싱하는 AI 포트폴리오 서비스입니다.
 */
@Slf4j
@Service
public class AiPortfolioService {
    private static final int RESPONSE_PREVIEW_LENGTH = 500;
    // 거래대금 상위 N개를 AI 후보 풀로 준다. 80개까지 넓혀봤으나 탈락이 줄지 않았다.
    // 모델이 부르는 코인 상당수는 목록 밖이 아니라 업비트에 존재하지도 않는 옛 티커였고,
    // 51~80위 구간은 유동성만 낮출 뿐 실익이 없어 되돌렸다. 부족분은 아래 보충 로직이 메운다
    private static final int UNIVERSE_POOL_SIZE = 50;
    // 스테이블코인은 가격이 고정되어 잔돈 투자 수익이 원천적으로 발생하지 않는다.
    // 특히 KRW-USDT 는 거래대금 1위라 보충 로직이 항상 가장 먼저 집게 되고,
    // 하락장에서는 등락률 0%가 모멘텀 점수 50점으로 하락 코인들을 앞질러 실제 매수까지 이어질 수 있다.
    // 프롬프트/검증/보충에 모두 반영되도록 유니버스 구성 단계에서 제외한다
    private static final Set<String> EXCLUDED_MARKETS = Set.of("KRW-USDT", "KRW-USDC", "KRW-USDE", "KRW-USDS");
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MILLIS = 2000L;
    // 환각 티커와 중복으로 일부가 탈락하므로 목표치보다 넉넉히 요청한다
    private static final int REQUESTED_CANDIDATE_COUNT = 18;
    // 2단계 스코어링에 넘길 최종 후보 수. 부족하면 거래대금 상위 코인으로 채워 항상 이 개수를 맞춘다
    private static final int TARGET_CANDIDATE_COUNT = 15;
    // AI가 최소한 이만큼은 유효 후보를 내야 한다. 이 아래면 보충으로 메우지 않고 재시도한다
    // (거의 전부를 보충으로 채우면 AI 추천이 아니라 그냥 거래대금 순위표가 된다)
    private static final int MIN_CANDIDATE_COUNT = 10;
    // 스케줄러가 12시간 주기이므로, 24시간을 넘겼다면 최소 2회 연속 실패한 것이다
    private static final long FALLBACK_STALE_HOURS = 24L;

    private final ChatClient chatClient;
    private final AlternativeClient alternativeClient;
    private final CoinGeckoClient coinGeckoClient;
    private final UpbitCandleClient upbitCandleClient;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper objectMapper;
    private final JsonMapper lenientJsonMapper;
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
        // LLM 응답에는 // 주석이나 후행 쉼표가 섞여 나온다. response-format 을 json_object 로 지정해도
        // DeepSeek 은 이를 완전히 막아주지 않으므로, AI 응답을 읽을 때만 두 아티팩트를 허용한다.
        // 캐시 직렬화(objectMapper)는 표준 JSON을 유지해야 하므로 전역 설정은 건드리지 않는다
        this.lenientJsonMapper = objectMapper.rebuild()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
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
        
        // 2. RAG Context: 거래대금 상위 활성 코인 추출 (9개 조합이 공유하므로 1회만 조회)
        ActiveCoinsContext activeCoinsContext = null;
        try {
            activeCoinsContext = getActiveCoinsContext();
        } catch (Exception e) {
            log.error("[AiPortfolioService] RAG 용 상위 {}개 코인 리스트 수집 실패", UNIVERSE_POOL_SIZE, e);
        }

        if (activeCoinsContext == null) {
            log.error("[AiPortfolioService] RAG 용 코인 리스트를 확보하지 못해 전체 조합을 Fallback 처리합니다");
        } else {
            log.info("[AiPortfolioService] 외부 데이터 및 RAG 용 코인 리스트 수집 완료 - marketCount: {}",
                    activeCoinsContext.allowedMarkets().size());
        }

        int successCount = 0;
        int failCount = 0;

        for (RiskTolerance risk : RiskTolerance.values()) {
            for (TrendSensitivity trend : TrendSensitivity.values()) {
                String hashKey = risk.name() + ":" + trend.name();

                // 컨텍스트가 없으면 AI를 호출하지 않는다 (환각 응답이 캐시에 적재되는 것을 막는다)
                if (activeCoinsContext == null) {
                    failCount++;
                    loadFallbackCandidates(hashKey);
                    continue;
                }

                try {
                    List<AiRecommendationDto> aiCandidates = fetchAiMacroCandidates(
                            risk, trend, fngIndex, btcDom, weeklyTrend, hotNarratives, macroEvents, activeCoinsContext);

                    String redisKey = "ai:candidates:" + hashKey;
                    String jsonValue = objectMapper.writeValueAsString(new AiCandidateResponse(aiCandidates));
                    redisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofHours(24));
                    log.info("[AiPortfolioService] 레디스에 후보군 캐싱 완료 - hashKey: {}, candidatesSize: {}", hashKey, aiCandidates.size());

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
     * Redis TTL(24시간)이 스케줄러 주기(12시간)보다 길어 캐시는 계속 채워지므로,
     * AI 호출이 며칠째 실패해도 겉으로는 정상으로 보입니다. 그래서 데이터 경과 시간을 반드시 남깁니다.
     */
    private void loadFallbackCandidates(String hashKey) {
        historyRepository.findTopByProfileHashKeyOrderByIdDesc(hashKey)
                .ifPresentOrElse(
                        history -> {
                            String redisKey = "ai:candidates:" + hashKey;
                            redisTemplate.opsForValue().set(redisKey, history.getCandidatesJson(), Duration.ofHours(24));

                            long ageHours = Duration.between(history.getCreatedAt(), LocalDateTime.now()).toHours();
                            if (ageHours >= FALLBACK_STALE_HOURS) {
                                log.error("[AiPortfolioService] Fallback 데이터가 낡았습니다. AI 호출이 장기간 실패 중입니다 - hashKey: {}, 생성 후 경과: {}시간",
                                        hashKey, ageHours);
                            } else {
                                log.warn("[AiPortfolioService] DB에서 이전 추천 데이터(Fallback)를 불러옵니다 - hashKey: {}, 생성 후 경과: {}시간",
                                        hashKey, ageHours);
                            }
                        },
                        () -> log.error("[AiPortfolioService] DB에 이전 데이터가 존재하지 않아 Fallback 불가. hashKey: {}", hashKey)
                );
    }

    /**
     * AI 프롬프트에 주입할 코인 목록과, 응답 검증·부족분 보충에 사용할 마켓 정보를 함께 담습니다.
     * 프롬프트에 넣은 목록과 검증 기준이 갈라지면 환각 티커를 걸러낼 수 없으므로 한 곳에서 만듭니다.
     * {@code marketNames}는 거래대금 내림차순을 유지하는 마켓코드 → 영문명 맵입니다.
     */
    private record ActiveCoinsContext(String promptText, Map<String, String> marketNames) {

        private Set<String> allowedMarkets() {
            return marketNames.keySet();
        }
    }

    /**
     * 거래대금 기준 상위 {@code UNIVERSE_POOL_SIZE}개의 코인 리스트를 생성하여 AI에게 RAG 컨텍스트로 제공합니다.
     */
    private ActiveCoinsContext getActiveCoinsContext() {
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

        List<UpbitTickerResponse> topTickers = tickers.stream()
                .filter(t -> t.accTradePrice24h() != null)
                .filter(t -> !EXCLUDED_MARKETS.contains(t.market()))
                .sorted((a, b) -> b.accTradePrice24h().compareTo(a.accTradePrice24h()))
                .limit(UNIVERSE_POOL_SIZE)
                .toList();

        if (topTickers.isEmpty()) {
            log.error("[AiPortfolioService] 업비트 시세 조회 결과가 비어 RAG 컨텍스트를 생성할 수 없습니다 - activeCoins: {}건", allCoins.size());
            return null;
        }

        Map<String, Coin> coinByMarket = allCoins.stream().collect(Collectors.toMap(Coin::getMarket, coin -> coin));

        StringBuilder sb = new StringBuilder();
        Map<String, String> marketNames = new LinkedHashMap<>();
        for (UpbitTickerResponse t : topTickers) {
            Coin coin = coinByMarket.get(t.market());
            String koreanName = (coin != null && coin.getKoreanName() != null) ? coin.getKoreanName() : "Unknown";
            // 보충 후보의 coinName 은 AI 응답과 형식을 맞추기 위해 영문명을 쓴다
            String englishName = (coin != null && coin.getEnglishName() != null) ? coin.getEnglishName() : t.market();

            sb.append("- ").append(t.market()).append(" (").append(koreanName).append(")\n");
            marketNames.put(t.market(), englishName);
        }
        return new ActiveCoinsContext(sb.toString(), marketNames);
    }

    private List<AiRecommendationDto> fetchAiMacroCandidates(RiskTolerance risk, TrendSensitivity trend,
            String fngIndex, String btcDom, String weeklyTrend, String hotNarratives, String macroEvents, ActiveCoinsContext activeCoinsContext) {

        String promptText = """
            You are the Lead Quantitative Strategist at a top-tier crypto hedge fund.
            Your task is to generate a highly optimized candidate pool of EXACTLY {requestCount} cryptocurrencies.
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

            [CRITICAL RAG CONTEXT: TOP {poolSize} ACTIVE COINS]
            You MUST select the {requestCount} coins ONLY from the following list of Top {poolSize} most actively traded coins right now:
            {activeCoinsContext}
            NEVER hallucinate or recommend a coin outside of this exact list!

            [Strategic Directives & Constraints]
            1. QUANTITY & SOURCE: You MUST select EXACTLY {requestCount} unique coins STRICTLY from the [TOP ACTIVE COINS] list above. Do not hallucinate tickers.
               - Every 'market' value you return MUST appear verbatim in that list. A ticker that is not in the list is discarded by our backend, shrinking the pool below the required size.
               - Ticker symbols change over time. Trust ONLY the list above, never your memory of what a coin's ticker used to be.
            2. THEME DIVERSITY: To prevent Pool Starvation in our backend engine, your {requestCount} selections MUST span across major themes. You must deduce the theme of each coin yourself.
               - Try to include coins from these themes: LAYER_1, DEFI, AI, WEB3_GAMING, RWA, MEME.
               - CRITICAL FALLBACK: If a specific theme (e.g., RWA or AI) does not exist in the provided [TOP ACTIVE COINS] list, DO NOT hallucinate. Simply allocate those slots to other available themes.
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

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 배치 작업이라 스트리밍을 소비할 대상이 없다. 논블로킹 스트림은 finishReason/usage 를 얻기 번거롭고
                // 장시간 열린 HTTP/2 스트림이 중간 장비에 끊기는 실패 경로만 늘어나므로 단건 호출을 쓴다
                ChatResponse chatResponse = chatClient.prompt()
                        .user(u -> u.text(promptText)
                                .param("fngIndex", fngIndex)
                                .param("btcDom", btcDom)
                                .param("weeklyTrend", weeklyTrend)
                                .param("hotNarratives", hotNarratives)
                                .param("macroEvents", macroEvents)
                                .param("riskTolerance", risk.name())
                                .param("trendSensitivity", trend.name())
                                .param("poolSize", String.valueOf(UNIVERSE_POOL_SIZE))
                                .param("requestCount", String.valueOf(REQUESTED_CANDIDATE_COUNT))
                                .param("activeCoinsContext", activeCoinsContext.promptText())
                        )
                        .call()
                        .chatResponse();

                if (chatResponse == null || chatResponse.getResult() == null) {
                    log.error("[AiPortfolioService] AI 응답이 null - risk: {}, trend: {}", risk.name(), trend.name());
                    throw new RuntimeException("JSON 형식의 응답이 아닙니다.");
                }

                // finishReason 이 LENGTH 면 프롬프트가 아니라 max-tokens 설정 문제다.
                // 이 값을 버리면 "빈 응답"과 "예산 초과로 잘린 응답"을 로그에서 구분할 수 없다
                String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                String usage = usageSummary(chatResponse);
                String responseText = chatResponse.getResult().getOutput().getText();

                if (responseText == null || responseText.isBlank()) {
                    log.error("[AiPortfolioService] AI 응답 본문이 비어 있음 - risk: {}, trend: {}, finishReason: {}, {}",
                            risk.name(), trend.name(), finishReason, usage);
                    throw new RuntimeException("JSON 형식의 응답이 아닙니다.");
                }

                int startIndex = responseText.indexOf('{');
                int endIndex = responseText.lastIndexOf('}');
                if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                    responseText = responseText.substring(startIndex, endIndex + 1);
                } else {
                    // 원문을 남기지 않으면 빈 응답인지 다른 형식인지 구분할 수 없어 원인 추적이 불가능하다
                    log.error("[AiPortfolioService] AI 응답에 JSON이 없음 - risk: {}, trend: {}, finishReason: {}, {}, length: {}, preview: {}",
                            risk.name(), trend.name(), finishReason, usage, responseText.length(), preview(responseText));
                    throw new RuntimeException("JSON 형식의 응답이 아닙니다.");
                }

                AiCandidateResponse response;
                try {
                    response = lenientJsonMapper.readValue(responseText, AiCandidateResponse.class);
                } catch (Exception e) {
                    // 예산 초과로 절단된 JSON은 위 브레이스 검사를 통과하므로 여기서 걸린다.
                    // 원문과 finishReason 없이는 절단인지 스키마 불일치인지 구분할 수 없다.
                    log.error("[AiPortfolioService] AI 응답 JSON 파싱 실패 - risk: {}, trend: {}, finishReason: {}, {}, length: {}, preview: {}",
                            risk.name(), trend.name(), finishReason, usage, responseText.length(), preview(responseText));
                    throw e;
                }

                List<AiRecommendationDto> validCandidates =
                        validateCandidates(response.candidates(), activeCoinsContext.allowedMarkets(), risk, trend, finishReason);
                List<AiRecommendationDto> candidates = fillToTargetCount(validCandidates, activeCoinsContext, risk, trend);

                log.info("[AiPortfolioService] AI 후보군 생성 완료 - candidatesSize: {}, risk: {}, trend: {}",
                        candidates.size(), risk.name(), trend.name());
                return candidates;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    log.error("[AiPortfolioService] AI 추천 API 호출 {}회 모두 실패 - errorMessage: {}", MAX_RETRIES, e.getMessage(), e);
                    throw new AiRecommendationFailedException();
                }
                log.warn("[AiPortfolioService] AI 추천 API 호출 실패, 재시도합니다... (시도 횟수: {}/{}) - errorMessage: {}", attempt, MAX_RETRIES, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw new AiRecommendationFailedException();
    }

    /**
     * AI가 돌려준 후보군에서 허용 목록 밖의 코인과 중복을 걸러내고, 최소 개수를 채웠는지 확인합니다.
     * 검증 없이 캐싱하면 절단된 응답이나 환각 티커가 그대로 실매수 후보로 쓰입니다.
     */
    private List<AiRecommendationDto> validateCandidates(List<AiRecommendationDto> candidates, Set<String> allowedMarkets,
            RiskTolerance risk, TrendSensitivity trend, String finishReason) {
        if (candidates == null || candidates.isEmpty()) {
            log.error("[AiPortfolioService] AI 후보군이 비어 있음 - risk: {}, trend: {}, finishReason: {}",
                    risk.name(), trend.name(), finishReason);
            throw new RuntimeException("AI 후보군이 비어 있습니다.");
        }

        List<AiRecommendationDto> validCandidates = new ArrayList<>();
        Set<String> seenMarkets = new LinkedHashSet<>();
        List<String> rejectedMarkets = new ArrayList<>();

        for (AiRecommendationDto candidate : candidates) {
            String market = candidate.market();
            if (market == null || !allowedMarkets.contains(market)) {
                rejectedMarkets.add(market);
                continue;
            }
            if (!seenMarkets.add(market)) {
                continue; // 같은 코인을 두 번 추천한 경우 뒤엣것을 버린다
            }
            validCandidates.add(candidate);
        }

        if (!rejectedMarkets.isEmpty()) {
            log.warn("[AiPortfolioService] 상위 {}개 목록에 없는 추천 코인을 제외했습니다 - risk: {}, trend: {}, rejected: {}",
                    UNIVERSE_POOL_SIZE, risk.name(), trend.name(), rejectedMarkets);
        }

        if (validCandidates.size() < MIN_CANDIDATE_COUNT) {
            log.error("[AiPortfolioService] 유효 후보군이 최소 개수에 미달 - risk: {}, trend: {}, finishReason: {}, validSize: {}, minSize: {}",
                    risk.name(), trend.name(), finishReason, validCandidates.size(), MIN_CANDIDATE_COUNT);
            throw new RuntimeException("유효한 후보군이 부족합니다.");
        }

        return validCandidates;
    }

    /**
     * 유효 후보가 목표 개수에 못 미치면 거래대금 상위 코인 중 아직 선택되지 않은 것으로 부족분을 채웁니다.
     * 모델이 존재하지 않는 옛 티커를 부르는 습관은 프롬프트로 완전히 막을 수 없으므로,
     * AI 응답 품질과 무관하게 후보 풀 크기를 보장하기 위한 장치입니다.
     * <p>
     * 보충분은 반드시 리스트 뒤에 붙입니다. 2단계 스코어링의 S_ai가 리스트 순서 기반이라
     * 뒤에 있을수록 낮은 점수를 받고, 그 결과 AI가 실제로 고른 코인이 항상 우선됩니다.
     * <p>
     * 보충분은 AI가 판단한 코인이 아니므로 테마·밈 속성을 알 수 없습니다.
     * 두 필드를 어떻게 채우는지와 그 이유는 아래 생성 부분의 주석을 참고하세요.
     */
    private List<AiRecommendationDto> fillToTargetCount(List<AiRecommendationDto> candidates,
            ActiveCoinsContext activeCoinsContext, RiskTolerance risk, TrendSensitivity trend) {
        if (candidates.size() >= TARGET_CANDIDATE_COUNT) {
            return candidates.stream().limit(TARGET_CANDIDATE_COUNT).toList();
        }

        Set<String> selectedMarkets = candidates.stream()
                .map(AiRecommendationDto::market)
                .collect(Collectors.toSet());

        List<AiRecommendationDto> filledCandidates = new ArrayList<>(candidates);
        List<String> filledMarkets = new ArrayList<>();

        for (Map.Entry<String, String> entry : activeCoinsContext.marketNames().entrySet()) {
            if (filledCandidates.size() >= TARGET_CANDIDATE_COUNT) {
                break;
            }
            if (selectedMarkets.contains(entry.getKey())) {
                continue;
            }
            // theme 을 임의로 채우면 사용자 테마와 우연히 일치해 만점을 받는다.
            // 보충분은 AI 판단이 아니므로 테마 점수를 받지 않도록 null 로 둔다.
            //
            // isMeme 은 true 로 둔다. COIN_METADATA 에 밈 여부가 없어 보충분을 분류할 수 없는데,
            // 거래대금 상위에는 밈 코인이 섞여 있다(예: KRW-BONK). false 로 두면 밈을 거부한
            // 사용자에게 밈 코인이 매수될 수 있으므로, 스코어링의 밈 컷오프에 걸리도록 보수적으로 표시한다.
            // 밈 허용 사용자에게는 가산점(+3.0)이 잘못 붙지만, 보충분은 S_ai/S_theme 이 최하위라
            // 순위를 뒤집지 못한다. 사용자 설정을 어기는 쪽보다 이 왜곡을 감수하는 편이 낫다
            filledCandidates.add(new AiRecommendationDto(entry.getKey(), entry.getValue(), "High liquidity fill", null, true));
            filledMarkets.add(entry.getKey());
        }

        log.info("[AiPortfolioService] 후보군 부족분을 거래대금 상위 코인으로 보충 - risk: {}, trend: {}, aiCount: {}, filled: {}",
                risk.name(), trend.name(), candidates.size(), filledMarkets);
        return filledCandidates;
    }

    /**
     * 실패 로그에 토큰 사용량을 함께 남깁니다.
     * completionTokens 가 max-tokens 에 붙어 있으면 응답이 예산 초과로 잘린 것이므로 원인 판별의 핵심 단서가 됩니다.
     */
    private String usageSummary(ChatResponse chatResponse) {
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return "usage: 없음";
        }
        return String.format("completionTokens: %s, totalTokens: %s", usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 재시도 사이에 짧은 대기를 두어 일시적인 장애가 회복될 여지를 줍니다.
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiRecommendationFailedException();
        }
    }

    public String generateProfileHashKey(InvestmentProfile profile) {
        String risk = profile.getRiskTolerance().name();
        String trend = profile.getTrendSensitivity().name();
        return String.join(":", risk, trend);
    }

    private void logFetchResult(String dataName, String dataValue) {
        // 빈 문자열도 수집 실패다. 성공으로 찍히면 데이터가 없는 채로 프롬프트가 구성된 것을 눈치챌 수 없다
        if (dataValue == null || dataValue.isBlank() || dataValue.startsWith("Unknown")) {
            log.warn("[AiPortfolioService] 외부 API 수집 실패 - {}: {}", dataName, dataValue);
        } else {
            log.info("[AiPortfolioService] 외부 API 수집 성공 - {}: {}", dataName, dataValue);
        }
    }

    /**
     * AI 응답 원문을 로그에 남기기 위해 앞부분만 잘라냅니다.
     * 전문을 남기면 프롬프트 컨텍스트까지 섞여 로그가 비대해지므로 원인 판별에 필요한 만큼만 남깁니다.
     */
    private String preview(String responseText) {
        // 공백만 있는 응답을 그대로 찍으면 로그에서 빈 응답과 구분되지 않는다
        if (responseText.isBlank()) {
            return "(빈 응답)";
        }
        return responseText.length() <= RESPONSE_PREVIEW_LENGTH
                ? responseText
                : responseText.substring(0, RESPONSE_PREVIEW_LENGTH) + "...(생략)";
    }
}