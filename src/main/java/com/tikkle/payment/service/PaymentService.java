package com.tikkle.payment.service;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.investment.repository.InvestmentSettingsRepository;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.dto.response.PaymentActionType;
import com.tikkle.payment.dto.response.PaymentScrapingResponse;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.PaymentCategoryMapping;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.payment.repository.PaymentCategoryMappingRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.service.component.SpareChangeCalculator;
import com.tikkle.upbit.service.UpbitTradeService;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentEventRepository paymentEventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentCategoryMappingRepository paymentCategoryMappingRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final InvestmentSettingsRepository investmentSettingsRepository;
    private final SpareChangeCalculator spareChangeCalculator;
    private final UpbitTradeService upbitTradeService;
    private final AiClassificationService aiClassificationService;
    private final CoinRepository coinRepository;

    @Transactional
    public PaymentScrapingResponse processPaymentScraping(PaymentScrapingRequest request) {
        // [1단계 Fail-Fast: Redis SETNX 중복 검증]
        String redisTxKey = "payment:tx:" + request.transactionId();
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisTxKey, "Y", 24, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(isFirstRequest)) {
            log.info("중복 결제 요청 조기 종료 (Redis Hit) - transactionId: {}", request.transactionId());
            return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_DUPLICATE, request.merchant(), request.amount(), 0, null, null, null, null);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    redisTemplate.delete(redisTxKey);
                }
            }
        });

        // [JPA 롤백 방지] DB 2차 검증
        if (paymentEventRepository.existsByTransactionId(request.transactionId())) {
            log.info("중복 결제 요청 조기 종료 (DB Hit) - transactionId: {}", request.transactionId());
            return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_DUPLICATE, request.merchant(), request.amount(), 0, null, null, null, null);
        }

        // Redis에서 유저 설정 통째로 가져오기
        Map<Object, Object> userSettings = getUserSettingsCacheOrDb(request.userId());

        String targetCardCompany = (String) userSettings.get("targetCardCompany");
        String targetCardLast4 = (String) userSettings.get("targetCardLast4");

        // [2단계 Fail-Fast: 타겟 카드 매칭 검증]
        // 이제 캐시 증발로 인한 null 확률은 해결됨. 오직 '진짜 다른 카드'이거나 '온보딩 미완료 유저'일 때만 스킵.
        if (targetCardCompany == null || targetCardLast4 == null ||
                !request.cardCompany().equals(targetCardCompany) ||
                !request.cardNumberLast4().equals(targetCardLast4)) {
            log.info("타겟 카드가 아니거나 온보딩 정보가 없어 조기 종료 (userId: {}, requestCard: {} {})",
                    request.userId(), request.cardCompany(), request.cardNumberLast4());
            redisTemplate.delete(redisTxKey);
            return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_CARD_MISMATCH, request.merchant(), request.amount(), 0, null, null, null, null);
        }

        // TODO : 추후 수정(더미 종목 주입)
        String dummyMarket = "KRW-BTC";
        String dummyCoinName = "비트코인";
        Coin targetCoin = coinRepository.findById(dummyMarket).orElse(null);

        // [Phase 1: DB HIT - 가맹점 1차 분류]
        List<PaymentCategoryMapping> mappings = paymentCategoryMappingRepository.findByKeywordContaining(request.merchant());
        if (!mappings.isEmpty()) {
            PaymentCategoryMapping mapping = mappings.get(0); // 가장 긴 키워드 매칭
            PaymentCategory category = mapping.getCategory();
            String keyword = mapping.getKeyword();

            // 💡 잔돈 룰 조회 (최적화: 이미 가져온 userSettings 맵 사용)
            RuleType ruleType = getRuleTypeFromCacheOrDb(request.userId(), category, userSettings);
            int spareChange = spareChangeCalculator.calculate(request.amount(), ruleType);

            if (spareChange == 0) {
                saveEvent(request, keyword, 0, PaymentStatus.NOT_INVESTED, "잔돈 0원", category, targetCoin);
                return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_NO_SPARE_CHANGE, keyword, request.amount(), 0, null, null, null, null);
            }

            if (spareChange < 5000) {
                saveEvent(request, keyword, spareChange, PaymentStatus.NOT_INVESTED, "최소 투자 금액(5,000원) 미달", category, targetCoin);
                return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_MINIMUM_AMOUNT_UNMET, keyword, request.amount(), spareChange, null, null, null, null);
            }

            ExecutionMode executionMode = getExecutionModeFromCacheOrDb(userSettings, request.userId());

            ExecutionResult result = executeTradeOrAwaitApproval(request, keyword, spareChange, category, executionMode, dummyMarket, targetCoin);

            Long eventId = result.savedEvent() != null ? result.savedEvent().getId() : null;
            return new PaymentScrapingResponse(eventId, result.actionType(), keyword, request.amount(), spareChange, dummyMarket, dummyCoinName, result.executedPrice(), result.executedVolume());
        }

        // [Phase 2: DB MISS - 동기식(Sync) AI 분류기로 이관]
        com.tikkle.payment.dto.response.AiClassificationResponse aiResponse;
        try {
            aiResponse = aiClassificationService.classify(request.merchant());
        } catch (Exception e) {
            log.warn("AI 분류 실패 또는 타임아웃 발생. merchant={}", request.merchant());
            // 타임아웃/실패 시 예외를 던지지 않고 200 OK 조기 종료 (DB 저장 X)
            return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_CLASSIFICATION_FAILED, request.merchant(), request.amount(), 0, null, null, null, null);
        }

        String keyword = aiResponse.keyword();
        PaymentCategory category = aiResponse.category();

        // 💡 1. 분류된 결과 캐싱 (다음 캐시 히트를 위함)
        try {
            paymentCategoryMappingRepository.save(
                    PaymentCategoryMapping.builder()
                            .keyword(keyword)
                            .category(category)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            log.info("이미 다른 요청에서 매핑 데이터를 저장했습니다. keyword={}", keyword);
        }

        // 💡 2. 잔돈 룰 조회 (최적화: 이미 가져온 userSettings 맵 사용)
        RuleType ruleType = getRuleTypeFromCacheOrDb(request.userId(), category, userSettings);
        int spareChange = spareChangeCalculator.calculate(request.amount(), ruleType);

        // 💡 3. 조기 차단(Fail-Fast) 방어선
        if (spareChange == 0) {
            saveEvent(request, keyword, 0, PaymentStatus.NOT_INVESTED, "잔돈 0원", category, targetCoin);
            return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_NO_SPARE_CHANGE, keyword, request.amount(), 0, null, null, null, null);
        }

        if (spareChange < 5000) {
            saveEvent(request, keyword, spareChange, PaymentStatus.NOT_INVESTED, "최소 투자 금액(5,000원) 미달", category, targetCoin);
            return new PaymentScrapingResponse(null, PaymentActionType.IGNORE_MINIMUM_AMOUNT_UNMET, keyword, request.amount(), spareChange, null, null, null, null);
        }

        ExecutionMode executionMode = getExecutionModeFromCacheOrDb(userSettings, request.userId());

        // 💡 4. 정상 파이프라인
        ExecutionResult result = executeTradeOrAwaitApproval(request, keyword, spareChange, category, executionMode, dummyMarket, targetCoin);

        Long eventId = result.savedEvent() != null ? result.savedEvent().getId() : null;
        return new PaymentScrapingResponse(eventId, result.actionType(), keyword, request.amount(), spareChange, dummyMarket, dummyCoinName, result.executedPrice(), result.executedVolume());
    }



    // 💡 [새로 추가된 캐시 복구(Re-warming) 메서드]
    private Map<Object, Object> getUserSettingsCacheOrDb(Long userId) {
        String redisKey = "user:settings:" + userId;
        Map<Object, Object> settings = redisTemplate.opsForHash().entries(redisKey);

        // 1. 캐시가 살아있으면 초고속 반환 (Zero-DB)
        if (settings != null && !settings.isEmpty() && settings.containsKey("targetCardCompany")) {
            return settings;
        }

        // 2. 캐시가 죽었으면 DB에서 조회 (Cache Miss)
        log.warn("유저(ID:{})의 Redis 캐시가 증발했습니다. DB에서 조회하여 복구합니다.", userId);

        var accountOpt = linkedAccountRepository.findByUserId(userId);
        var settingsOpt = investmentSettingsRepository.findByUserId(userId);

        // DB에도 정보가 없다면 온보딩을 안 한 유저 (빈 Map 반환하여 Fail-Fast 되도록 유도)
        if (accountOpt.isEmpty() || settingsOpt.isEmpty()) {
            return new HashMap<>();
        }

        var account = accountOpt.get();
        var investSettings = settingsOpt.get();
        List<CategorySpareChangeRule> rules = categorySpareChangeRuleRepository.findByUserId(userId);

        Map<String, String> cacheData = new HashMap<>();
        cacheData.put("targetCardCompany", account.getTargetCardCompany());
        cacheData.put("targetCardLast4", account.getTargetCardLast4());
        cacheData.put("executionMode", investSettings.getExecutionMode().name());
        rules.forEach(rule -> {
            if (rule.getCategory() == null) {
                cacheData.put("DEFAULT_RULE", rule.getRuleType().name());
            } else {
                cacheData.put(rule.getCategory().name(), rule.getRuleType().name());
            }
        });

        // 3. 찾은 데이터를 Redis에 다시 적재 (Re-warming)
        redisTemplate.opsForHash().putAll(redisKey, cacheData);

        return new HashMap<>(cacheData);
    }

    private ExecutionMode getExecutionModeFromCacheOrDb(Map<Object, Object> userSettings, Long userId) {
        String executionModeStr = (String) userSettings.get("executionMode");
        if (executionModeStr != null) {
            return ExecutionMode.valueOf(executionModeStr);
        }
        return investmentSettingsRepository.findByUserId(userId)
                .map(com.tikkle.investment.entity.InvestmentSettings::getExecutionMode)
                .orElse(ExecutionMode.MANUAL);
    }

    private RuleType getRuleTypeFromCacheOrDb(Long userId, PaymentCategory category, Map<Object, Object> userSettings) {
        String ruleTypeStr = (String) userSettings.get(category.name());

        if (ruleTypeStr != null) {
            return RuleType.valueOf(ruleTypeStr);
        }

        log.warn("유저(ID:{})의 Redis 캐시에 {} 카테고리 룰이 존재하지 않습니다. DB에서 조회합니다.", userId, category.name());
        // 데이터 소스 단일화 처리 완료
        return categorySpareChangeRuleRepository.findByUserIdAndCategory(userId, category)
                .or(() -> categorySpareChangeRuleRepository.findDefaultByUserId(userId))
                .map(CategorySpareChangeRule::getRuleType)
                .orElse(RuleType.ROUND_UP_10000);
    }

    private PaymentEvent saveEvent(PaymentScrapingRequest request, String keyword, Integer spareChange, PaymentStatus status, String reason, PaymentCategory category, Coin targetCoin) {
        log.info("결제 이벤트 저장 시도. status: {}, spareChange: {}", status, spareChange);
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .userId(request.userId())
                    .cardCompany(request.cardCompany())
                    .cardNumberLast4(request.cardNumberLast4())
                    .merchant(keyword != null ? keyword : request.merchant())
                    .amount(request.amount())
                    .spareChange(spareChange)
                    .category(category)
                    .transactionId(request.transactionId())
                    .status(status)
                    .reason(reason)
                    .targetCoin(targetCoin)
                    .build();

            return paymentEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("DataIntegrityViolationException 발생 (ConstraintViolationException). 동시성 중복 결제 요청일 가능성이 높습니다. transactionId={}, cause={}",
                        request.transactionId(), e.getMessage());
                return null;
            } else {
                throw e;
            }
        }
    }

    private record ExecutionResult(PaymentStatus status, PaymentActionType actionType, BigDecimal executedPrice, BigDecimal executedVolume, String reason, PaymentEvent savedEvent) {}

    private ExecutionResult executeTradeOrAwaitApproval(PaymentScrapingRequest request, String keyword, int spareChange, PaymentCategory category, ExecutionMode executionMode, String dummyMarket, Coin targetCoin) {
        if (executionMode == ExecutionMode.MANUAL) {
            PaymentEvent savedEvent = saveEvent(request, keyword, spareChange, PaymentStatus.WAITING_APPROVAL, null, category, targetCoin);
            return new ExecutionResult(PaymentStatus.WAITING_APPROVAL, PaymentActionType.WAITING_APPROVAL, null, null, null, savedEvent);
        } else {
            // 외부 호출 전 ORDERING 상태로 선 저장 (원장 불일치 방어)
            PaymentEvent savedEvent = saveEvent(request, keyword, spareChange, PaymentStatus.ORDERING, null, category, targetCoin);
            try {
                UpbitTradeService.TradeResult tradeResult = upbitTradeService.executeTrade(request.userId(), dummyMarket, spareChange);
                // 성공 시 상태 업데이트
                savedEvent.completeInvestment();
                return new ExecutionResult(PaymentStatus.INVESTED, PaymentActionType.ORDER_REQUESTED, tradeResult.executedPrice(), tradeResult.executedVolume(), null, savedEvent);
            } catch (Exception e) {
                log.error("업비트 자동 매수 실패", e);
                // 실패 시 상태 업데이트
                savedEvent.failInvestment(e.getMessage());
                return new ExecutionResult(PaymentStatus.FAILED, PaymentActionType.UPBIT_ORDER_FAILED, null, null, e.getMessage(), savedEvent);
            }
        }
    }
}