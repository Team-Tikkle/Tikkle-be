package com.tikkle.payment.service;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.exception.InvalidPaymentStatusException;
import com.tikkle.payment.exception.UpbitTradeException;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.service.UpbitTradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매수 승인 및 거절 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
public class OrderApprovalService {
    private static final String DEFAULT_FALLBACK_MARKET = "KRW-BTC";

    private final PaymentEventRepository paymentEventRepository;
    private final UpbitTradeService upbitTradeService;
    private final OrderApprovalService self;

    public OrderApprovalService(
            PaymentEventRepository paymentEventRepository,
            UpbitTradeService upbitTradeService,
            @Lazy OrderApprovalService self
    ) {
        this.paymentEventRepository = paymentEventRepository;
        this.upbitTradeService = upbitTradeService;
        this.self = self;
    }

    @Transactional
    public void approveOrder(Long userId, Long eventId) {
        PaymentEvent event = paymentEventRepository.findByIdAndUserIdForUpdate(eventId, userId)
                .orElseThrow(PaymentEventNotFoundException::new);

        if (event.getStatus() != PaymentStatus.PENDING_PURCHASE) {
            throw new InvalidPaymentStatusException();
        }

        try {
            // 업비트 동기 매수 및 원장 업데이트 (AI가 지정한 타겟 코인 매수)
            String targetMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : DEFAULT_FALLBACK_MARKET;
            var result = upbitTradeService.executeTrade(event.getUserId(), targetMarket, event.getSpareChange());
            event.completeInvestment(result.executedVolume(), result.executedPrice());
        } catch (Exception e) {
            log.error("[OrderApprovalService] 매수 승인 후 업비트 체결 실패 - eventId: {}", eventId, e);
            String reason = "업비트 매수 주문 실패: " + e.getMessage();
            self.markAsFailed(eventId, reason);
            throw new UpbitTradeException();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long eventId, String reason) {
        paymentEventRepository.findById(eventId).ifPresent(event -> {
            event.failInvestment(reason);
        });
    }

    @Transactional
    public void rejectOrder(Long userId, Long eventId) {
        PaymentEvent event = paymentEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(PaymentEventNotFoundException::new);

        if (event.getStatus() != PaymentStatus.PENDING_PURCHASE) {
            throw new InvalidPaymentStatusException();
        }

        event.skipInvestment("사용자에 의한 매수 거절");
    }
}