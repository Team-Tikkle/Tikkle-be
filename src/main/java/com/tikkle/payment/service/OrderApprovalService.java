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

    /**
     * 대기 중인 결제 건에 대해 매수를 승인하고, 업비트 API를 통해 동기적으로 매수 주문을 체결합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 매수를 승인할 결제 이벤트 ID
     */
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

    /**
     * 매수 주문 실패 시 이벤트 상태를 실패(FAILED)로 마킹합니다. 
     * 본 트랜잭션은 독립된 트랜잭션으로 실행되어 롤백과 무관하게 실패 기록을 남깁니다.
     *
     * @param eventId 실패 처리할 결제 이벤트 ID
     * @param reason 실패 사유
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long eventId, String reason) {
        paymentEventRepository.findById(eventId).ifPresent(event -> {
            event.failInvestment(reason);
        });
    }

    /**
     * 대기 중인 결제 건에 대해 매수를 거절(취소) 처리합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 거절할 결제 이벤트 ID
     */
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