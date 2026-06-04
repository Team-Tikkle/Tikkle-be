package com.tikkle.payment.service;

import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private final PaymentEventRepository paymentEventRepository;

    public void processPaymentScraping(PaymentScrapingRequest request) {
        BigDecimal amount = BigDecimal.valueOf(request.amount());

        // TODO: 추후 온보딩 시 설정한 기본 잔돈 규칙이나, 각 결제 카테고리별로 사용자가 개별 설정한 잔돈 규칙을 Redis 등에서 조회하도록 수정 필요.
        BigDecimal rule = BigDecimal.valueOf(1000);

        // 1. 잔돈 계산 (나머지 연산 활용)
        BigDecimal remainder = amount.remainder(rule);
        BigDecimal spareChange = remainder.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : rule.subtract(remainder);

        // 잔돈이 0원 발생한 결제 건은 무시 (투자할 금액이 없음)
        if (spareChange.compareTo(BigDecimal.ZERO) == 0) {
            log.info("잔돈이 0원이므로 투자를 진행하지 않습니다. (결제금액: {})", amount);
            return;
        }

        // 2. PaymentEvent 엔티티 생성 (초기 상태: PENDING)
        PaymentEvent paymentEvent = PaymentEvent.builder()
                .userId(request.userId())
                .merchantName(request.merchant())
                .amount(amount)
                .spareChangeAmount(spareChange)
                .build();

        // 3. Fail-Fast: 잔액 부족 검사
        if (BigDecimal.valueOf(request.balance()).compareTo(spareChange) < 0) {
            log.warn("잔액 부족으로 투자를 취소합니다. (userId: {})", request.userId());
            paymentEvent.fail("잔액 부족"); // 상태를 FAILED로 변경
            paymentEventRepository.save(paymentEvent);
            return;
        }

        // 4. 이벤트 원장 저장 (결제가 정상적으로 접수됨)
        paymentEventRepository.save(paymentEvent);
        log.info("결제 이벤트 접수 완료. 잔돈: {}", spareChange);
        
        // TODO: 추후 모든 처리가 완료되었을 대, PENDING -> SUCCESS로 바꾸는 로직 추가
    }
}
