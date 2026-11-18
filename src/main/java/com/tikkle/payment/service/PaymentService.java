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
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
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
    // PAYMENT_EVENTS.merchant 컬럼 길이. 분류 키워드는 이 컬럼에 저장된다
    private static final int KEYWORD_MAX_LENGTH = 100;

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
     * 중복으로 거절한 요청은 멱등 키를 그대로 두고, 처리하지 못했거나 업무 규칙으로 거절한 요청만 키를 지웁니다.
     * 
     * @param request 안드로이드에서 수신된 결제 정보 DTO
     * @return 결제 처리 결과 및 추천 코인 정보 DTO
     */
    public PaymentScrapingResponse processPayment(PaymentScrapingRequest request) {
        // 1단계: 결제 검증
        String redisTxKey = "payment:tx:" + request.transactionId();

        // 같은 거래를 먼저 받은 요청이 있으면 그 요청이 남긴 키를 건드리지 않고 끝낸다
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisTxKey, "Y", 24, TimeUnit.HOURS);

        if (Boolean.FALSE.equals(isFirstRequest)) {
            log.info("[PaymentService] 중복 결제 요청 조기 종료 (Redis Hit) - transactionId: {}", request.transactionId());
            throw new DuplicatePaymentException();
        }

        try {
            // Redis에서 유저 설정 통째로 가져오기
            Map<String, String> userSettings = settingsCacheService.getUserSettings(request.userId());

            String isInvestmentEnabled = userSettings.get("isInvestmentEnabled");
            if ("false".equals(isInvestmentEnabled)) {
                log.info("[PaymentService] 자동 투자가 비활성화되어 결제 처리 조기 종료 - userId: {}", request.userId());
                throw new InvestmentDisabledException();
            }

            // 타겟 카드 매칭 검증
            validateTargetCard(request, userSettings);

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

            PaymentEvent savedEvent = saveEventOrRejectDuplicate(request, classification.keyword(), spareChange,
                    PaymentStatus.PENDING_PURCHASE, null, classification.category(), recommendation.coin());

            return new PaymentScrapingResponse(savedEvent.getId(), PaymentActionType.PENDING_PURCHASE,
                    classification.keyword(), request.amount(), spareChange,
                    recommendation.market(), recommendation.coinName());
        } catch (DuplicatePaymentException e) {
            // 원장에 이미 있는 거래다. 방금 선점한 키를 남겨 다음 중복 요청이 Redis 단계에서 끝나게 한다
            throw e;
        } catch (Exception e) {
            // 처리하지 못했거나 업무 규칙으로 거절한 요청은 키를 지워, 같은 요청이 다시 오면 다시 판정한다
            log.error("[PaymentService] 결제 처리 중 오류 발생, Redis 캐시 정리 - transactionId: {}", request.transactionId(), e);
            redisTemplate.delete(redisTxKey);
            throw e;
        }
    }

    /**
     * 타겟 카드 매칭 검증. 불일치 시 예외 발생.
     */
    private void validateTargetCard(PaymentScrapingRequest request, Map<String, String> userSettings) {
        String targetCardCompany = userSettings.get("targetCardCompany");
        String targetCardLast4 = userSettings.get("targetCardLast4");

        if (targetCardCompany == null || targetCardLast4 == null ||
                !request.cardCompany().equals(targetCardCompany) ||
                !request.cardNumberLast4().equals(targetCardLast4)) {
            log.info("[PaymentService] 타겟 카드 불일치 또는 온보딩 정보 부재 - userId: {}, requestCard: {} {}",
                    request.userId(), request.cardCompany(), request.cardNumberLast4());
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
        String keyword = fitKeywordLength(aiResponse.keyword());

        // 분류 결과 저장
        try {
            paymentCategoryMappingRepository.save(
                    PaymentCategoryMapping.builder()
                            .keyword(keyword)
                            .category(aiResponse.category())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            log.info("[PaymentService] 이미 다른 요청에서 매핑 데이터를 저장했습니다 - keyword: {}", keyword);
        }

        return new ClassificationResult(keyword, aiResponse.category());
    }

    /**
     * AI가 돌려준 키워드는 길이가 정해져 있지 않다. 원장 컬럼보다 길면 저장이 실패하므로 컬럼 길이에 맞게 자른다.
     */
    private static String fitKeywordLength(String keyword) {
        if (keyword.length() <= KEYWORD_MAX_LENGTH) {
            return keyword;
        }
        int end = KEYWORD_MAX_LENGTH;
        // 이모지 같은 2칸 문자의 앞쪽만 남으면 저장할 수 없는 문자가 되므로 한 칸 더 줄인다
        if (Character.isHighSurrogate(keyword.charAt(end - 1))) {
            end--;
        }
        return keyword.substring(0, end);
    }

    /**
     * NOT_INVESTED 상태로 원장을 저장하고 조기 차단 응답을 생성한다.
     */
    private PaymentScrapingResponse handleNotInvested(PaymentScrapingRequest request, ClassificationResult classification,
                                                      int spareChange, String reason, PaymentActionType actionType) {
        // NOT_INVESTED 이벤트도 코인 추천 없이 저장
        saveEventOrRejectDuplicate(request, classification.keyword(), spareChange, PaymentStatus.NOT_INVESTED, reason, classification.category(), null);
        return new PaymentScrapingResponse(null, actionType, classification.keyword(), request.amount(), spareChange, null, null);
    }

    /**
     * 결제 이벤트를 저장하고, 같은 거래가 원장에 이미 있으면(유니크 제약 위반) 중복 요청으로 거절한다.
     * 제약 위반이 나면 저장 트랜잭션은 롤백 전용이 되어, 그 안에서 예외를 잡아도 커밋 단계에서 다시 실패한다.
     * 그래서 트랜잭션이 끝난 뒤인 여기서 잡는다. 유니크가 아닌 제약 위반은 중복이 아니므로 그대로 던진다.
     */
    private PaymentEvent saveEventOrRejectDuplicate(PaymentScrapingRequest request, String keyword, Integer spareChange,
                                                    PaymentStatus status, String reason, PaymentCategory category, Coin targetCoin) {
        try {
            return self.saveEvent(request, keyword, spareChange, status, reason, category, targetCoin);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation && violation.getKind() == ConstraintKind.UNIQUE) {
                log.warn("[PaymentService] 원장에 이미 있는 결제 요청 (DB Constraint Violation) - transactionId: {}, cause: {}",
                        request.transactionId(), e.getMessage());
                throw new DuplicatePaymentException();
            }
            throw e;
        }
    }

    /**
     * 결제 이벤트를 원장에 저장한다. 제약 위반은 잡지 않고 그대로 던진다.
     */
    @Transactional
    public PaymentEvent saveEvent(PaymentScrapingRequest request, String keyword, Integer spareChange,
                                   PaymentStatus status, String reason, PaymentCategory category, Coin targetCoin) {
        log.info("[PaymentService] 결제 이벤트 저장 시도 - status: {}, spareChange: {}", status, spareChange);
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
    }
}