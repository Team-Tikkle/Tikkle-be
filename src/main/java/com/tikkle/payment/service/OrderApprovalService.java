package com.tikkle.payment.service;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.exception.InvalidPaymentStatusException;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.exception.UpbitTradeException;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.client.UpbitDepositClient;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
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
    private final PaymentEventRepository paymentEventRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitDepositClient upbitDepositClient;
    private final OrderApprovalService self;

    public OrderApprovalService(
            PaymentEventRepository paymentEventRepository,
            LinkedAccountRepository linkedAccountRepository,
            UpbitDepositClient upbitDepositClient,
            @Lazy OrderApprovalService self
    ) {
        this.paymentEventRepository = paymentEventRepository;
        this.linkedAccountRepository = linkedAccountRepository;
        this.upbitDepositClient = upbitDepositClient;
        this.self = self;
    }

    /**
     * 대기 중인 결제 건에 대해 매수를 승인하고, 업비트 API를 통해 원화 입금을 요청(2차 인증 발송)합니다.
     * 성공 시 결제 이벤트 상태를 PENDING_DEPOSIT으로 변경합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 매수를 승인할 결제 이벤트 ID
     */
    @Transactional(noRollbackFor = UpbitTradeException.class)
    public void approveOrder(Long userId, Long eventId) {
        PaymentEvent event = paymentEventRepository.findByIdAndUserIdForUpdate(eventId, userId)
                .orElseThrow(PaymentEventNotFoundException::new);

        if (event.getStatus() != PaymentStatus.PENDING_PURCHASE) {
            throw new InvalidPaymentStatusException();
        }

        try {
            LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                    .orElseThrow(LinkedAccountNotFoundException::new);

            String twoFactorType = account.getTwoFactorProvider().getUpbitProviderType();

            // 업비트 원화 입금 요청 (2차 인증 발송)
            var depositResponse = upbitDepositClient.requestKrwDeposit(
                    event.getSpareChange().intValue(),
                    twoFactorType,
                    account.getUpbitAccessKey(),
                    account.getUpbitSecretKey()
            );

            // 상태를 PENDING_DEPOSIT으로 변경하고 uuid 저장
            event.updateToPendingDeposit(depositResponse.uuid());
            log.info("[OrderApprovalService] 업비트 입금 요청 성공 - eventId: {}, uuid: {}", eventId, depositResponse.uuid());
        } catch (Exception e) {
            log.error("[OrderApprovalService] 매수 승인(입금 요청) 실패 - eventId: {}", eventId, e);
            String reason = "업비트 입금 요청 실패: " + e.getMessage();
            
            // 데드락 방지: REQUIRES_NEW 트랜잭션(self.markAsFailed)을 타지 않고 현재 영속성 컨텍스트에서 바로 변경 후 커밋 유도
            event.failInvestment(reason);
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