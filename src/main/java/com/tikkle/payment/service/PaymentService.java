package com.tikkle.payment.service;

import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.service.TargetCoinRecommendationService;
import com.tikkle.investment.service.TargetCoinRecommendationService.CoinRecommendation;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.dto.response.AiClassificationResponse;
import com.tikkle.payment.dto.response.PaymentActionType;
import com.tikkle.payment.dto.response.PaymentScrapingResponse;
import com.tikkle.payment.entity.PaymentCategoryMapping;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.exception.DuplicatePaymentException;
import com.tikkle.payment.exception.InvestmentDisabledException;
import com.tikkle.payment.exception.CardMismatchException;
import com.tikkle.user.exception.NoCategoryRuleException;
import com.tikkle.payment.repository.PaymentCategoryMappingRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.service.component.SpareChangeCalculator;
import com.tikkle.settings.service.SettingsCacheService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 결제 도메인의 핵심 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
public class PaymentService {
    private final PaymentEventRepository paymentEventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentCategoryMappingRepository paymentCategoryMappingRepository;
    private final SpareChangeCalculator spareChangeCalculator;
    private final AiClassificationService aiClassificationService;
    private final TargetCoinRecommendationService targetCoinRecommendationService;
    private final SettingsCacheService settingsCacheService;
    private final PaymentService self;

    public PaymentService(
            PaymentEventRepository paymentEventRepository,
            RedisTemplate<String, String> redisTemplate,
            PaymentCategoryMappingRepository paymentCategoryMappingRepository,
            SpareChangeCalculator spareChangeCalculator,
            AiClassificationService aiClassificationService,
            TargetCoinRecommendationService targetCoinRecommendationService,
            SettingsCacheService settingsCacheService,
            @Lazy PaymentService self) {
        this.paymentEventRepository = paymentEventRepository;
        this.redisTemplate = redisTemplate;
        this.paymentCategoryMappingRepository = paymentCategoryMappingRepository;
        this.spareChangeCalculator = spareChangeCalculator;
        this.aiClassificationService = aiClassificationService;
        this.targetCoinRecommendationService = targetCoinRecommendationService;
        this.settingsCacheService = settingsCacheService;
        this.self = self;
    }

    // 내부 전달용 record
    private record ClassificationResult(String keyword, PaymentCategory category) {}

    /**
     * 결제 스크래핑 요청을 처리하는 메인 파이프라인 메서드입니다.
     * 중복 결제 검증, AI 카테고리 분류, 잔돈 규칙 적용, 타겟 코인 추천 후 결제 이벤트를 영속화합니다.
     * 
     * @param request 안드로이드에서 수신된 결제 정보 DTO
     * @return 결제 처리 결과 및 추천 코인 정보 DTO
     */
    public PaymentScrapingResponse processPayment(PaymentScrapingRequest request) {
        // 1단계: 결제 검증
        String redisTxKey = "payment:tx:" + request.transactionId();

        try {
            Boolean isFirstRequest = redisTemplate.opsForValue()
                    .setIfAbsent(redisTxKey, "Y", 24, TimeUnit.HOURS);

            if (Boolean.FALSE.equals(isFirstRequest)) {
                log.info("[PaymentService] 중복 결제 요청 조기 종료 (Redis Hit) - transactionId: {}", request.transactionId());
                throw new DuplicatePaymentException();
            }

            // Redis에서 유저 설정 통째로 가져오기
            Map<String, String> userSettings = settingsCacheService.getUserSettings(request.userId());

            String isInvestmentEnabled = userSettings.get("isInvestmentEnabled");
            if ("false".equals(isInvestmentEnabled)) {
                log.info("[PaymentService] 자동 투자가 비활성화되어 결제 처리 조기 종료 - userId: {}", request.userId());
                redisTemplate.delete(redisTxKey);
                throw new InvestmentDisabledException();
            }

            // 타겟 카드 매칭 검증
            validateTargetCard(request, userSettings, redisTxKey);

            // 2단계: 카테고리 분류
            ClassificationResult classification = classifyMerchant(request.merchant());

            // 3단계: 잔돈 계산
            String ruleTypeStr = userSettings.get(classification.category().name());
            if (ruleTypeStr == null) {
                log.error("[PaymentService] 투자 규칙 설정 미존재 - userId: {}, category: {}", request.userId(), classification.category());
                throw new NoCategoryRuleException();
            }
            RuleType ruleType = RuleType.valueOf(ruleTypeStr);
            int spareChange = spareChangeCalculator.calculate(request.amount(), ruleType);

            // 잔돈 0원 → 조기 차단
            if (spareChange == 0) {
                return handleNotInvested(request, classification, 0, "잔돈 0원", PaymentActionType.IGNORE_NO_SPARE_CHANGE);
            }

            // 최소 투자 금액(5,100원) 미달 → 조기 차단
            if (spareChange < 5100) {
                return handleNotInvested(request, classification, spareChange, "최소 투자 금액(5,100원) 미달", PaymentActionType.IGNORE_MINIMUM_AMOUNT_UNMET);
            }

            // 4단계: 코인 추천 후 응답
            CoinRecommendation recommendation = targetCoinRecommendationService.recommendCoin(request.userId());

            PaymentEvent savedEvent = self.saveEvent(request, classification.keyword(), spareChange,
                    PaymentStatus.PENDING_PURCHASE, null, classification.category(), recommendation.coin());

            if (savedEvent == null) {
                log.info("[PaymentService] 동시성 중복 결제 감지 (DB Constraint Violation) - transactionId: {}", request.transactionId());
                throw new DuplicatePaymentException();
            }

            return new PaymentScrapingResponse(savedEvent.getId(), PaymentActionType.PENDING_PURCHASE,
                    classification.keyword(), request.amount(), spareChange,
                    recommendation.market(), recommendation.coinName());
        } catch (Exception e) {
            log.error("[PaymentService] 결제 처리 중 오류 발생, Redis 캐시 정리 - transactionId: {}", request.transactionId(), e);
            redisTemplate.delete(redisTxKey);
            throw e;
        }
    }

    /**
     * 타겟 카드 매칭 검증. 불일치 시 예외 발생.
     */
    private void validateTargetCard(PaymentScrapingRequest request, Map<String, String> userSettings, String redisTxKey) {
        String targetCardCompany = userSettings.get("targetCardCompany");
        String targetCardLast4 = userSettings.get("targetCardLast4");

        if (targetCardCompany == null || targetCardLast4 == null ||
                !request.cardCompany().equals(targetCardCompany) ||
                !request.cardNumberLast4().equals(targetCardLast4)) {
            log.info("[PaymentService] 타겟 카드 불일치 또는 온보딩 정보 부재 - userId: {}, requestCard: {} {}",
                    request.userId(), request.cardCompany(), request.cardNumberLast4());
            redisTemplate.delete(redisTxKey);
            throw new CardMismatchException();
        }
    }

    /**
     * DB 캐시(PaymentCategoryMapping) 우선 조회 → 미스 시 AI 분류 폴백
     */
    private ClassificationResult classifyMerchant(String merchant) {
        // Phase 1: DB HIT
        List<PaymentCategoryMapping> mappings = paymentCategoryMappingRepository.findByKeywordContaining(merchant);
        if (!mappings.isEmpty()) {
            PaymentCategoryMapping mapping = mappings.get(0);
            return new ClassificationResult(mapping.getKeyword(), mapping.getCategory());
        }

        // Phase 2: AI 분류
        AiClassificationResponse aiResponse = aiClassificationService.classify(merchant);

        // 분류 결과 저장
        try {
            paymentCategoryMappingRepository.save(
                    PaymentCategoryMapping.builder()
                            .keyword(aiResponse.keyword())
                            .category(aiResponse.category())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            log.info("[PaymentService] 이미 다른 요청에서 매핑 데이터를 저장했습니다 - keyword: {}", aiResponse.keyword());
        }

        return new ClassificationResult(aiResponse.keyword(), aiResponse.category());
    }

    /**
     * NOT_INVESTED 상태로 원장을 저장하고 조기 차단 응답을 생성한다.
     */
    private PaymentScrapingResponse handleNotInvested(PaymentScrapingRequest request, ClassificationResult classification,
                                                      int spareChange, String reason, PaymentActionType actionType) {
        // NOT_INVESTED 이벤트도 코인 추천 없이 저장
        self.saveEvent(request, classification.keyword(), spareChange, PaymentStatus.NOT_INVESTED, reason, classification.category(), null);
        return new PaymentScrapingResponse(null, actionType, classification.keyword(), request.amount(), spareChange, null, null);
    }

    @Transactional
    public PaymentEvent saveEvent(PaymentScrapingRequest request, String keyword, Integer spareChange,
                                   PaymentStatus status, String reason, PaymentCategory category, Coin targetCoin) {
        log.info("[PaymentService] 결제 이벤트 저장 시도 - status: {}, spareChange: {}", status, spareChange);
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .userId(request.userId())
                    .cardCompany(request.cardCompany())
                    .cardNumberLast4(request.cardNumberLast4())
                    .merchant(keyword != null ? keyword : request.merchant())
                    .rawMerchant(request.merchant())
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
                log.warn("[PaymentService] DataIntegrityViolationException 발생. 동시성 중복 결제 가능성 - transactionId: {}, cause: {}",
                        request.transactionId(), e.getMessage());
                return null;
            } else {
                throw e;
            }
        }
    }
}