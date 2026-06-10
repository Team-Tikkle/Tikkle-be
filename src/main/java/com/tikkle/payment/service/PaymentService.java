package com.tikkle.payment.service;

import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private final PaymentEventRepository paymentEventRepository;
    private final UserRepository userRepository;

    public void processPaymentScraping(PaymentScrapingRequest request) {
        // [1단계 Fail-Fast: 중복 결제 검증]
        if (paymentEventRepository.existsByTransactionId(request.transactionId())) {
            log.info("중복 결제 요청 조기 종료 (transactionId: {})", request.transactionId());
            return;
        }

        // [2단계 Fail-Fast: 타겟 카드 매칭 검증]
        User user = userRepository.findById(request.userId()).orElseThrow(UserNotFoundException::new);

        if (!request.cardCompany().equals(user.getTargetCardCompany()) ||
            !request.cardNumberLast4().equals(user.getTargetCardNumberLast4())) {
            log.info("타겟 카드가 아니므로 조기 종료 (userId: {}, requestCard: {} {})",
                     user.getId(), request.cardCompany(), request.cardNumberLast4());
            return;
        }

        Integer amount = request.amount();
        // TODO: 추후 온보딩 시 설정한 기본 잔돈 규칙이나, 각 결제 카테고리별로 사용자가 개별 설정한 잔돈 규칙을 Redis 등에서 조회하도록 수정 필요.
        Integer rule = 1000;

        Integer remainder = amount % rule;
        Integer spareChange = remainder == 0 ? 0 : rule - remainder;

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .userId(request.userId())
                .cardCompany(request.cardCompany())
                .cardNumberLast4(request.cardNumberLast4())
                .merchant(request.merchant())
                .amount(amount)
                .spareChange(spareChange)
                .transactionId(request.transactionId())
                .build();

        try {
            // [3단계 Fail-Fast: 자잔돈 0원 결제 건 이력 관리]
            if (spareChange == 0) {
                paymentEventRepository.save(paymentEvent);
                log.info("잔돈이 0원이므로 투자안함 상태로 저장하고 조기 종료합니다. (결제금액: {})", amount);
                return;
            }

            paymentEventRepository.save(paymentEvent);
            log.info("결제 이벤트 접수 완료. 잔돈: {}", spareChange);
            
            // TODO: [1차 방어선] Caffeine 로컬 캐시 조회 로직 추가
            // TODO: [2차 방어선] 캐시 MISS 시 RabbitMQ로 분류 요청 이벤트 발행 로직 추가

        } catch (DataIntegrityViolationException e) {
            // 아주 짧은 순간에 두 개의 동일한 요청이 동시에 들어오는 경우, 첫 번째 검증을 둘 다 통과할 수 있음
            // 이 때 DB의 UNIQUE 제약조건이 동작하여 DataIntegrityViolationException이 발생
            if (e.getCause() instanceof ConstraintViolationException) {
                log.info("동시성 중복 결제 요청 무시. transactionId={}", request.transactionId());
            } else {
                throw e;
            }
        }
    }
}