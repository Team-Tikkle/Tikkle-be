package com.tikkle.payment.service;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.investment.repository.InvestmentSettingsRepository;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.entity.PaymentCategoryMapping;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.event.ClassificationRequestEvent;
import com.tikkle.payment.repository.PaymentCategoryMappingRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.service.component.MarketTimeGate;
import com.tikkle.payment.service.component.SpareChangeCalculator;
import com.tikkle.user.repository.LinkedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
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
    private final MarketTimeGate marketTimeGate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void processPaymentScraping(PaymentScrapingRequest request) {
        // [1단계 Fail-Fast: Redis SETNX 중복 검증]
        String redisTxKey = "payment:tx:" + request.transactionId();
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisTxKey, "Y", 24, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(isFirstRequest)) {
            log.info("중복 결제 요청 조기 종료 (Redis Hit) - transactionId: {}", request.transactionId());
            return;
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
            return;
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
            return;
        }

        String redisSettingsKey = "user:settings:" + request.userId();

        // [Phase 1: DB HIT - 가맹점 1차 분류]
        List<PaymentCategoryMapping> mappings = paymentCategoryMappingRepository.findByKeywordContaining(request.merchant());
        if (!mappings.isEmpty()) {
            PaymentCategoryMapping mapping = mappings.get(0); // 가장 긴 키워드 매칭
            PaymentCategory category = mapping.getCategory();

            // 💡 잔돈 룰 조회
            RuleType ruleType = getRuleTypeFromCacheOrDb(request.userId(), category, redisSettingsKey);
            int spareChange = spareChangeCalculator.calculate(request.amount(), ruleType);

            if (spareChange == 0) {
                saveEvent(request, 0, PaymentStatus.NOT_INVESTED, "잔돈 0원", category);
                return;
            }

            // 💡 ExecutionMode를 Redis에서 조회하여 MarketTimeGate 판별
            String execModeStr = (String) userSettings.get("executionMode");
            ExecutionMode executionMode = (execModeStr != null) ? ExecutionMode.valueOf(execModeStr) : ExecutionMode.MANUAL;

            PaymentStatus status = marketTimeGate.getPaymentStatus(executionMode);
            saveEvent(request, spareChange, status, null, category);
            return;
        }

        // [Phase 2: DB MISS - AI 분류기로 이관]
        // Null Constraint 방어를 위해 잔돈에 0원을 세팅하여 임시 원장 저장
        PaymentEvent tempEvent = saveEvent(request, 0, PaymentStatus.CLASSIFYING, "가맹점 분류 대기", null);
        if (tempEvent != null) {
            eventPublisher.publishEvent(new ClassificationRequestEvent(this, tempEvent.getId(), request.merchant()));
        }
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
        rules.forEach(rule -> cacheData.put(rule.getCategory().name(), rule.getRuleType().name()));

        // 3. 찾은 데이터를 Redis에 다시 적재 (Re-warming)
        redisTemplate.opsForHash().putAll(redisKey, cacheData);

        return new HashMap<>(cacheData);
    }

    private RuleType getRuleTypeFromCacheOrDb(Long userId, PaymentCategory category, String redisSettingsKey) {
        String ruleTypeStr = (String) redisTemplate.opsForHash().get(redisSettingsKey, category.name());

        if (ruleTypeStr != null) {
            return RuleType.valueOf(ruleTypeStr);
        }

        log.warn("유저(ID:{})의 Redis 캐시에 {} 카테고리 룰이 존재하지 않습니다. DB에서 조회합니다.", userId, category.name());
        // 데이터 소스 단일화 처리 완료
        return categorySpareChangeRuleRepository.findByUserIdAndCategory(userId, category)
                .or(() -> categorySpareChangeRuleRepository.findDefaultByUserId(userId))
                .map(CategorySpareChangeRule::getRuleType)
                .orElse(RuleType.ROUND_UP_1000);
    }

    private PaymentEvent saveEvent(PaymentScrapingRequest request, Integer spareChange, PaymentStatus status, String reason, PaymentCategory category) {
        log.info("결제 이벤트 저장 시도. status: {}, spareChange: {}", status, spareChange);
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .userId(request.userId())
                    .cardCompany(request.cardCompany())
                    .cardNumberLast4(request.cardNumberLast4())
                    .merchant(request.merchant())
                    .amount(request.amount())
                    .spareChange(spareChange)
                    .category(category)
                    .transactionId(request.transactionId())
                    .status(status)
                    .reason(reason)
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
}