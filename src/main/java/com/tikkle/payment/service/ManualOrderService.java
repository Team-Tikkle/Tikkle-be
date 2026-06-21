package com.tikkle.payment.service;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;
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

    // 더미 데이터 주입: AI 연동 전까지 하드코딩
    private static final String DUMMY_MARKET = "KRW-BTC";

    @Transactional
    public void approveOrder(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR)); // TODO: NotFoundException 정의

        if (event.getStatus() != PaymentStatus.WAITING_APPROVAL) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR); // TODO: InvalidStatusException 정의
        }

        try {
            // 업비트 동기 매수 및 원장 업데이트
            upbitTradeService.executeTrade(event.getUserId(), DUMMY_MARKET, event.getSpareChange());
            event.completeInvestment();
        } catch (Exception e) {
            log.error("수동 승인 후 업비트 체결 실패", e);
            event.failInvestment(e.getMessage());
            throw e; // Controller Advisor 등에서 처리
        }
    }

    @Transactional
    public void rejectOrder(Long eventId) {
        PaymentEvent event = paymentEventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR)); // TODO: NotFoundException 정의

        if (event.getStatus() != PaymentStatus.WAITING_APPROVAL) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR); // TODO: InvalidStatusException 정의
        }

        event.skipInvestment("사용자에 의한 수동 매수 거절");
    }
}
