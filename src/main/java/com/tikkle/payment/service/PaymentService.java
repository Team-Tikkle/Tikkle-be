package com.tikkle.payment.service;

import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentEventRepository paymentEventRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public void processPaymentScraping(PaymentScrapingRequest request) {
        // [1단계 Fail-Fast: Redis SETNX를 이용한 완벽하고 빠른 중복 검증]
        String redisKey = "payment:tx:" + request.transactionId();
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "Y", 24, TimeUnit.HOURS); // 24시간 TTL

        if (Boolean.FALSE.equals(isFirstRequest)) {
            log.info("중복 결제 요청 조기 종료 (Redis Hit) - transactionId: {}", request.transactionId());
            return;
        }

        // 혹시 모를 캐시 만료/유실에 대비한 DB 2차 검증
        if (paymentEventRepository.existsByTransactionId(request.transactionId())) {
            log.info("중복 결제 요청 조기 종료 (DB Hit) - transactionId: {}", request.transactionId());
            return;
        }

        // [2단계 Fail-Fast: 타겟 카드 매칭 검증]
        User user = userRepository.findById(request.userId()).orElseThrow(UserNotFoundException::new);
        if (!isTargetCard(user, request)) {
            log.info("타겟 카드가 아니므로 조기 종료 (userId: {}, requestCard: {} {})",
                    user.getId(), request.cardCompany(), request.cardNumberLast4());
            return;
        }

        // [3단계 Fail-Fast: 잔돈 0원 결제 건 처리]
        Integer spareChange = calculateSpareChange(request.amount());
        if (spareChange == 0) {
            saveEvent(request, 0, PaymentStatus.NOT_INVESTED, "잔돈 0원");
            return;
        }

        // 정상 결제 건 처리
        saveEvent(request, spareChange, PaymentStatus.PENDING, null);
    }

    private void saveEvent(PaymentScrapingRequest request, Integer spareChange, PaymentStatus status, String reason) {
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
            paymentEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // Redis 1차 방어, existsByTransactionId 2차 방어에도 불구하고 동시성 이슈 발생 시 처리
            log.warn("DataIntegrityViolationException 발생. 동시성 중복 결제 요청일 가능성이 높습니다. transactionId={}", request.transactionId());
        }
    }

    private boolean isTargetCard(User user, PaymentScrapingRequest request) {
        return request.cardCompany().equals(user.getTargetCardCompany()) &&
                request.cardNumberLast4().equals(user.getTargetCardNumberLast4());
    }

    private Integer calculateSpareChange(Integer amount) {
        // TODO: 추후 온보딩 시 설정한 기본 잔돈 규칙이나, 각 결제 카테고리별로 사용자가 개별 설정한 잔돈 규칙을 Redis 등에서 조회하도록 수정 필요.
        final int ROUND_UP_UNIT = 1000;
        if (amount % ROUND_UP_UNIT == 0) {
            return 0;
        }
        return ROUND_UP_UNIT - (amount % ROUND_UP_UNIT);
    }
}