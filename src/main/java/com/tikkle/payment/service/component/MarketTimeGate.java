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
        LocalDateTime now = LocalDateTime.now(clock);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        boolean isWeekday = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        boolean isMarketHours = isWeekday && !currentTime.isBefore(MARKET_OPEN_TIME) && currentTime.isBefore(MARKET_CLOSE_TIME);

        if (isMarketHours) {
            return executionMode == ExecutionMode.AUTO ? PaymentStatus.ORDERING : PaymentStatus.WAITING_APPROVAL;
        } else {
            return executionMode == ExecutionMode.AUTO ? PaymentStatus.PENDING : PaymentStatus.PENDING_APPROVAL;
        }
    }
}