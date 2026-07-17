package com.tikkle.payment.service;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.exception.InvalidPaymentStatusException;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.exception.UpbitTradeException;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.upbit.client.UpbitDepositClient;
import com.tikkle.upbit.dto.response.UpbitDepositResponse;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.exception.InvalidTwoFactorProviderException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매수 승인 및 거절 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApprovalService {
    private final PaymentEventRepository paymentEventRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitDepositClient upbitDepositClient;

    /**
     * 대기 중인 결제 건에 대해 매수를 승인하고, 업비트 API를 통해 원화 입금을 요청(2차 인증 발송)합니다.
     * 성공 시 결제 이벤트 상태를 PENDING_DEPOSIT으로 변경합니다.
     *
     * <p>입금 요청이 실패하면 결제 건을 실패로 마킹하지 않고 PENDING_PURCHASE 그대로 둡니다.
     * 실제 출금은 사용자가 2차 인증을 완료해야 일어나므로 요청 실패 시점에는 되돌릴 것이 없고,
     * 실패로 확정하면 되살릴 방법이 없어 일시적 오류만으로 투자 기회가 사라지기 때문입니다.
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

        LinkedAccount account = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        if (account.getTwoFactorProvider() == null) {
            throw new InvalidTwoFactorProviderException();
        }

        UpbitDepositResponse depositResponse = requestDeposit(eventId, event, account);

        event.updateToPendingDeposit(depositResponse.uuid());
        log.info("[OrderApprovalService] 업비트 입금 요청 성공 - eventId: {}, uuid: {}", eventId, depositResponse.uuid());
    }

    /**
     * 업비트에 원화 입금(2차 인증 발송)을 요청합니다.
     * 도메인 예외는 각자의 ErrorCode를 유지한 채 전달하고, 그 외 예외만 UpbitTradeException으로 감쌉니다.
     */
    private UpbitDepositResponse requestDeposit(Long eventId, PaymentEvent event, LinkedAccount account) {
        try {
            return upbitDepositClient.requestKrwDeposit(
                    event.getSpareChange().intValue(),
                    account.getTwoFactorProvider().getUpbitProviderType(),
                    account.getUpbitAccessKey(),
                    account.getUpbitSecretKey()
            );
        } catch (CustomException e) {
            log.error("[OrderApprovalService] 매수 승인 실패 (도메인 예외) - eventId: {}", eventId, e);
            throw e;
        } catch (Exception e) {
            log.error("[OrderApprovalService] 매수 승인(입금 요청) 실패 - eventId: {}", eventId, e);
            throw new UpbitTradeException();
        }
    }

    /**
     * SSE 구독 전에 해당 결제 건이 요청자 본인의 것인지 검증합니다.
     * 남의 결제 건 존재 여부가 드러나지 않도록 소유자가 아니면 없는 건과 동일하게 처리합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 구독할 결제 이벤트 ID
     */
    @Transactional(readOnly = true)
    public void validateEventOwnership(Long userId, Long eventId) {
        if (!paymentEventRepository.existsByIdAndUserId(eventId, userId)) {
            log.warn("[OrderApprovalService] 타인의 결제 건 구독 시도 차단 - userId: {}, eventId: {}", userId, eventId);
            throw new PaymentEventNotFoundException();
        }
    }

    /**
     * 매수 주문 실패 시 이벤트 상태를 실패(FAILED)로 마킹합니다.
     * 본 트랜잭션은 독립된 트랜잭션으로 실행되어 롤백과 무관하게 실패 기록을 남깁니다.
     *
     * @param eventId 실패 처리할 결제 이벤트 ID
     * @param reason 실패 사유 (백엔드 확인용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long eventId, String reason) {
        paymentEventRepository.findById(eventId).ifPresent(event -> event.failInvestment(reason));
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
