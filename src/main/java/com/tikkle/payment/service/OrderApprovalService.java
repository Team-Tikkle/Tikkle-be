package com.tikkle.payment.service;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.exception.InvalidPaymentStatusException;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
import com.tikkle.payment.exception.UpbitTradeException;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.sse.SseConnectionManager;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

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
    private final SseConnectionManager sseConnectionManager;

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
     * 결제 진행 상황 SSE를 구독합니다.
     * 남의 결제 건 존재 여부가 드러나지 않도록 소유자가 아니면 없는 건과 동일하게 처리합니다.
     *
     * <p>2차 인증은 카카오·네이버 등 외부 앱에서 완료하므로 이 구간에서 앱 이탈은 정상 경로이고,
     * 그때 SSE는 반드시 끊깁니다. 따라서 구독 직후 현재 상태를 1회 내려보내
     * 끊겨 있는 동안 발생한 변화를 복구할 수 있게 합니다.
     *
     * @param userId 사용자 ID
     * @param eventId 구독할 결제 이벤트 ID
     * @return 결제 진행 상황을 전달하는 SseEmitter
     */
    @Transactional(readOnly = true)
    public SseEmitter subscribe(Long userId, Long eventId) {
        PaymentEvent event = paymentEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> {
                    log.warn("[OrderApprovalService] 타인의 결제 건 구독 시도 차단 - userId: {}, eventId: {}", userId, eventId);
                    return new PaymentEventNotFoundException();
                });

        SseEmitter emitter = sseConnectionManager.createEmitter(eventId);
        publishCurrentStatus(event);
        return emitter;
    }

    /**
     * 구독 직후 결제 건의 현재 상태를 1회 발송합니다.
     * 이미 종료된 건이면 최종 이벤트를 보내고 연결을 즉시 닫아, 클라이언트가 210초를 헛되이 대기하지 않게 합니다.
     */
    private void publishCurrentStatus(PaymentEvent event) {
        Long eventId = event.getId();
        log.info("[OrderApprovalService] SSE 구독 시점 상태 스냅샷 발송 - eventId: {}, status: {}", eventId, event.getStatus());

        // data 형태는 기존 발송 지점과 동일하게 맞춘다. TIMEOUT/PROCESSING만 평문 문자열, 나머지는 JSON.
        switch (event.getStatus()) {
            case PENDING_DEPOSIT -> sseConnectionManager.send(eventId, "PROCESSING", "업비트 입금 2차 인증 대기 중");
            case PENDING_PURCHASE -> sendFinal(eventId, "TIMEOUT",
                    "진행 중인 승인 건이 없습니다. 결제 건은 PENDING_PURCHASE 상태로 재승인 가능");
            case PENDING_TRADE -> sendFinal(eventId, "PENDING_TRADE", Map.of(
                    "status", "PENDING_TRADE",
                    "message", "주문 접수됨. 체결 결과는 결제 내역 재조회 또는 푸시 알림으로 확인 필요"));
            case INVESTED -> sendInvested(event);
            case FAILED -> sendFinal(eventId, "FAILED", Map.of(
                    "status", "FAILED",
                    "message", "매수가 실패로 종료된 결제 건"));
            case NOT_INVESTED -> sendFinal(eventId, "CLOSED", Map.of(
                    "status", "CLOSED",
                    "message", "투자가 진행되지 않고 종료된 결제 건"));
        }
    }

    private void sendFinal(Long eventId, String name, Object data) {
        sseConnectionManager.send(eventId, name, data);
        sseConnectionManager.complete(eventId);
    }

    private void sendInvested(PaymentEvent event) {
        Map<String, Object> successData = new LinkedHashMap<>();
        successData.put("status", "SUCCESS");
        successData.put("message", "시장가 매수 체결 완료");
        successData.put("targetCoinName", event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : "코인");
        successData.put("investedVolume", event.getInvestedVolume());
        successData.put("investedPrice", event.getInvestedPrice());

        sseConnectionManager.send(event.getId(), "SUCCESS", successData);
        sseConnectionManager.complete(event.getId());
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
