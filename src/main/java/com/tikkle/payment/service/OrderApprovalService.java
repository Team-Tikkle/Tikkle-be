package com.tikkle.payment.service;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.exception.UnknownPaymentStatusException;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.service.UpbitTradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        PaymentEvent event = paymentEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(PaymentEventNotFoundException::new);

        if (event.getStatus() != PaymentStatus.PENDING_PURCHASE) {
            throw new UnknownPaymentStatusException();
        }

        try {
            // 업비트 동기 매수 및 원장 업데이트 (AI가 지정한 타겟 코인 매수)
            String targetMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : DEFAULT_FALLBACK_MARKET;
            upbitTradeService.executeTrade(event.getUserId(), targetMarket, event.getSpareChange());
            event.completeInvestment();
        } catch (Exception e) {
            log.error("매수 승인 후 업비트 체결 실패", e);
            self.markAsFailed(eventId, e.getMessage());
            throw e; // Controller Advisor 등에서 처리
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
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
            throw new UnknownPaymentStatusException();
        }

        event.skipInvestment("사용자에 의한 매수 거절");
    }
}