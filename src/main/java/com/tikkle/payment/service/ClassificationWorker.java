package com.tikkle.payment.service;

import com.tikkle.investment.entity.InvestmentSettings;
import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.investment.repository.InvestmentSettingsRepository;
import com.tikkle.payment.dto.response.AiClassificationResponse;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.PaymentCategoryMapping;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.entity.enums.RuleType;
import com.tikkle.payment.event.ClassificationRequestEvent;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.repository.CategorySpareChangeRuleRepository;
import com.tikkle.payment.repository.PaymentCategoryMappingRepository;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.service.component.MarketTimeGate;
import com.tikkle.payment.service.component.SpareChangeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassificationWorker {
    private final AiClassificationService aiClassificationService;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentCategoryMappingRepository paymentCategoryMappingRepository;
    private final InvestmentSettingsRepository investmentSettingsRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final SpareChangeCalculator spareChangeCalculator;
    private final MarketTimeGate marketTimeGate;

    @Async("aiWorkerExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleClassificationRequest(ClassificationRequestEvent event) {
        log.info("[AI Worker] 비동기 분류 작업 시작. PaymentEvent ID: {}", event.getPaymentEventId());

        PaymentEvent paymentEvent = paymentEventRepository.findById(event.getPaymentEventId())
                .orElseThrow(PaymentEventNotFoundException::new);

        AiClassificationResponse classificationResult;
        try {
            classificationResult = aiClassificationService.classify(event.getMerchant());
        } catch (Exception e) {
            log.error("[AI Worker] AI 분류 실패. Fallback 로직을 실행합니다. PaymentEvent ID: {}", event.getPaymentEventId(), e);
            classificationResult = new AiClassificationResponse("기타", PaymentCategory.ETC);
        }

        // 해당 키워드가 없는 경우 AI 학습 데이터 저장
        if (paymentCategoryMappingRepository.findByKeyword(classificationResult.keyword()).isEmpty()) {
            paymentCategoryMappingRepository.save(
                    PaymentCategoryMapping.builder()
                            .keyword(classificationResult.keyword())
                            .category(classificationResult.category())
                            .build()
            );
        }

        // 잔돈 계산 규칙 조회
        RuleType ruleType = categorySpareChangeRuleRepository.findByUserIdAndCategory(paymentEvent.getUserId(), classificationResult.category())
                .map(CategorySpareChangeRule::getRuleType)
                .orElse(RuleType.ROUND_UP_1000); // 해당 카테고리 설정이 없으면 기본값(1000원) 적용
        int spareChange = spareChangeCalculator.calculate(paymentEvent.getAmount(), ruleType);

        // 투자 실행 방식 조회
        ExecutionMode executionMode = investmentSettingsRepository.findByUserId(paymentEvent.getUserId())
                .map(InvestmentSettings::getExecutionMode)
                .orElse(ExecutionMode.AUTO); // 투자 설정이 없으면 기본값(자동) 적용
        PaymentStatus finalStatus = marketTimeGate.getPaymentStatus(executionMode);

        // 원장 업데이트
        paymentEvent.updateAfterClassification(finalStatus, spareChange, classificationResult.category());

        log.info("[AI Worker] 비동기 분류 작업 완료. PaymentEvent ID: {}, 최종 상태: {}", event.getPaymentEventId(), finalStatus);
    }
}