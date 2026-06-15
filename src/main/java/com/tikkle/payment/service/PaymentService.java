package com.tikkle.payment.service;

import com.tikkle.investment.entity.enums.ExecutionMode;
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
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.UserRule;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import com.tikkle.user.repository.UserRuleRepository;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentEventRepository paymentEventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PaymentCategoryMappingRepository paymentCategoryMappingRepository;
    private final UserRuleRepository userRuleRepository;
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

        // 💡 Redis에서 유저 설정 통째로 가져오기 (타겟카드, 룰, 매매방식)
        String redisSettingsKey = "user:settings:" + request.userId();

        // [2단계 Fail-Fast: 타겟 카드 매칭 검증 - Zero DB!]
        String targetCardLast4 = (String) redisTemplate.opsForHash().get(redisSettingsKey, "targetCardLast4");

        // 카드사가 일치하는지(PG사 필터링 로직 등)는 클라이언트가 넘겨준 값과 내부 정책으로 비교 (여기선 번호 4자리만 검증 예시)
        if (targetCardLast4 == null || !request.cardNumberLast4().equals(targetCardLast4)) {
            log.info("타겟 카드가 아니거나 캐시가 없어 조기 종료 (userId: {}, requestCardLast4: {})",
                    request.userId(), request.cardNumberLast4());
            return;
        }

        // [Phase 1: DB HIT - 가맹점 1차 분류]
        List<PaymentCategoryMapping> mappings = paymentCategoryMappingRepository.findByKeywordContaining(request.merchant());
        if (!mappings.isEmpty()) {
            PaymentCategoryMapping mapping = mappings.get(0); // 가장 긴 키워드 매칭
            PaymentCategory category = mapping.getCategory();

            // 💡 잔돈 룰 조회
            RuleType ruleType = getRuleTypeFromCacheOrDb(request.userId(), category, redisSettingsKey);
            int spareChange = spareChangeCalculator.calculate(request.amount(), ruleType);

            if (spareChange == 0) {
                saveEvent(request, 0, PaymentStatus.NOT_INVESTED, "잔돈 0원");
                return;
            }

            // 💡 ExecutionMode를 Redis에서 조회하여 MarketTimeGate 판별
            String execModeStr = (String) redisTemplate.opsForHash().get(redisSettingsKey, "executionMode");
            ExecutionMode executionMode = (execModeStr != null) ? ExecutionMode.valueOf(execModeStr) : ExecutionMode.MANUAL; // 기본값 Fallback

            PaymentStatus status = marketTimeGate.getPaymentStatus(executionMode);
            saveEvent(request, spareChange, status, null);
            return;
        }

        // [Phase 2: DB MISS - AI 분류기로 이관]
        PaymentEvent tempEvent = saveEvent(request, null, PaymentStatus.CLASSIFYING, "가맹점 분류 대기");
        if (tempEvent != null) {
            eventPublisher.publishEvent(new ClassificationRequestEvent(this, tempEvent.getId(), request.merchant()));
        }
    }

    private RuleType getRuleTypeFromCacheOrDb(Long userId, PaymentCategory category, String redisSettingsKey) {
        String ruleTypeStr = (String) redisTemplate.opsForHash().get(redisSettingsKey, category.name());

        if (ruleTypeStr != null) {
            return RuleType.valueOf(ruleTypeStr);
        }

        log.warn("유저(ID:{})의 Redis 캐시에 {} 카테고리 룰이 존재하지 않습니다. DB에서 조회합니다.", userId, category.name());
        return userRuleRepository.findByUserIdAndCategory(userId, category)
                .or(() -> userRuleRepository.findDefaultByUserId(userId))
                .map(UserRule::getRuleType)
                .orElse(RuleType.ROUND_UP_1000);
    }

    private PaymentEvent saveEvent(PaymentScrapingRequest request, Integer spareChange, PaymentStatus status, String reason) {
        log.info("결제 이벤트 저장 시도. status: {}, spareChange: {}", status, spareChange);
        try {
            PaymentEvent event = PaymentEvent.builder()
                    .userId(request.userId())
                    .cardCompany(request.cardCompany())
                    .cardNumberLast4(request.cardNumberLast4())
                    .merchant(request.merchant())
                    .amount(request.amount())
                    .spareChange(spareChange)
                    .transactionId(request.transactionId())
                    .status(status)
                    .reason(reason)
                    .build();

            return paymentEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // 동시성 이슈로 인한 중복 키 예외인지 확인
            if (e.getCause() instanceof ConstraintViolationException) {
                log.warn("DataIntegrityViolationException 발생 (ConstraintViolationException). 동시성 중복 결제 요청일 가능성이 높습니다. transactionId={}, cause={}",
                        request.transactionId(), e.getMessage());
                // 이미 다른 트랜잭션에서 처리되었을 가능성이 높으므로, null을 반환하여 후속 처리가 없도록 합니다.
                return null;
            } else {
                // 예상치 못한 DB 제약조건 위반이므로 예외를 다시 던져서 트랜잭션 롤백
                throw e;
            }
        }
    }

    private boolean isTargetCard(LinkedAccount linkedAccount, PaymentScrapingRequest request) {
        return request.cardCompany().equals(linkedAccount.getTargetCardCompany()) &&
                request.cardNumberLast4().equals(linkedAccount.getTargetCardLast4());
    }
}