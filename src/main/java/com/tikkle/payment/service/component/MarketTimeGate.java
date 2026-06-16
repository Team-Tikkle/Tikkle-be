package com.tikkle.payment.service.component;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.payment.entity.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class MarketTimeGate {
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);

    private final Clock clock; // 테스트 용이성을 위한 DI 주입

    /**
     * 유저의 매매 방식(AUTO/MANUAL)과 시장 시간에 따라
     * 결제 이벤트 원장이 최초로 가져야 할 상태를 결정합니다.
     */
    public PaymentStatus getPaymentStatus(ExecutionMode executionMode) {
        // 1. 수동 매매(MANUAL)는 시장 시간과 무관하게 무조건 승인 대기
        if (executionMode == ExecutionMode.MANUAL) {
            return PaymentStatus.WAITING_APPROVAL;
        }

        // 2. 자동 매매(AUTO)일 경우에만 시간 판별 진행
        LocalDateTime now = LocalDateTime.now(clock);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        boolean isWeekday = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        boolean isMarketHours = !currentTime.isBefore(MARKET_OPEN_TIME) && currentTime.isBefore(MARKET_CLOSE_TIME);

        // 평일 장중이면 주문(ORDERING), 장외/주말이면 대기(PENDING)
        if (isWeekday && isMarketHours) {
            return PaymentStatus.ORDERING;
        } else {
            return PaymentStatus.PENDING;
        }
    }
}