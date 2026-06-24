package com.tikkle.payment.service;

import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.exception.UnknownPaymentStatusException;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.service.UpbitTradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualOrderService {

    private final PaymentEventRepository paymentEventRepository;
    private final UpbitTradeService upbitTradeService;
    private final org.springframework.beans.factory.ObjectProvider<ManualOrderService> selfProvider;

    @Transactional
    public void approveOrder(Long userId, Long eventId) {
        PaymentEvent event = paymentEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(PaymentEventNotFoundException::new);

        if (event.getStatus() != PaymentStatus.WAITING_APPROVAL) {
            throw new UnknownPaymentStatusException();
        }

        try {
            // 업비트 동기 매수 및 원장 업데이트 (AI가 지정한 타겟 코인 매수)
            String targetMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : "KRW-BTC";
            upbitTradeService.executeTrade(event.getUserId(), targetMarket, event.getSpareChange());
            event.completeInvestment();
        } catch (Exception e) {
            log.error("수동 승인 후 업비트 체결 실패", e);
            selfProvider.getObject().markAsFailed(eventId, e.getMessage());
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

        if (event.getStatus() != PaymentStatus.WAITING_APPROVAL) {
            throw new UnknownPaymentStatusException();
        }

        event.skipInvestment("사용자에 의한 수동 매수 거절");
    }
}