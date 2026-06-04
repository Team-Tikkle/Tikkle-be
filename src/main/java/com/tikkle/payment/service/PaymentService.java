package com.tikkle.payment.service;

import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
        String idempotencyKey = generateIdempotencyKey(request);

        BigDecimal amount = BigDecimal.valueOf(request.amount());
        // TODO: 추후 온보딩 시 설정한 기본 잔돈 규칙이나, 각 결제 카테고리별로 사용자가 개별 설정한 잔돈 규칙을 Redis 등에서 조회하도록 수정 필요.
        BigDecimal rule = BigDecimal.valueOf(1000);

        // 1. 잔돈 계산 (나머지 연산 활용)
        BigDecimal remainder = amount.remainder(rule);
        BigDecimal spareChange = remainder.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : rule.subtract(remainder);

        // 2. PaymentEvent 엔티티 생성 (초기 상태: PENDING)
        PaymentEvent paymentEvent = PaymentEvent.builder()
                .userId(request.userId())
                .merchantName(request.merchant())
                .amount(amount)
                .spareChangeAmount(spareChange)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            // 잔돈이 0원인 경우에도 기록
            if (spareChange.compareTo(BigDecimal.ZERO) == 0) {
                log.info("잔돈이 0원이므로 투자를 진행하지 않습니다. (결제금액: {})", amount);
                paymentEventRepository.save(paymentEvent);
                return;
            }

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
        } catch (DataIntegrityViolationException e) {
            // 예외의 원인이 제약 조건 위반(ConstraintViolationException)인지 확인
            if (e.getCause() instanceof ConstraintViolationException) {
                // 제약 조건 위반 예외의 SQL 상태 코드를 확인하거나, 제약 조건 이름을 확인하여 더 정확하게 판단할 수 있음
                // 여기서는 고유 키 제약 조건 이름(예: "uk_idempotency_key")을 확인하는 것이 가장 정확함
                // 편의상, 여기서는 제약 조건 위반이 발생하면 멱등성 키 충돌로 간주하고 로그를 남김
                log.info("중복 결제 요청 무시 (concurrent insert). idempotencyKey={}", idempotencyKey);
            } else {
                // 멱등성 키 중복이 아닌 다른 데이터 무결성 문제(예: NOT NULL 제약 조건 위반)일 경우, 예외를 다시 던져서 문제를 인지시킴
                throw e;
            }
        }
    }

    private String generateIdempotencyKey(PaymentScrapingRequest request) {
        // 재시도 시에도 동일한 키가 생성되도록 클라이언트가 제공하는 transactionId를 사용
        return String.format("%d:%s:%d:%s",
                request.userId(),
                request.merchant(),
                request.amount(),
                request.transactionId()
        );
    }
}