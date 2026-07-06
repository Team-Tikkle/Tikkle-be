package com.tikkle.payment.service;


import com.tikkle.global.exception.InvalidInputValueException;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 결제 내역 피드 및 대시보드 통계 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentHistoryService {
    private final PaymentEventRepository paymentEventRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PaymentDashboardResponse getDashboard(String email, String month) {
        User user = getUserByEmail(email);
        Long userId = user.getId();

        YearMonth yearMonth = parseMonth(month);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);

        // 1. 해당 월 데이터 일괄 조회
        List<PaymentEvent> events = paymentEventRepository.findByUserIdAndCreatedAtBetween(userId, startOfMonth, endOfMonth);

        // 2. 전체 누적 대기 건수 카운트
        long pendingCount = paymentEventRepository.countByUserIdAndStatus(userId, PaymentStatus.PENDING_PURCHASE);

        // 3. 메모리 집계
        long totalPayment = 0;
        long totalInvestedChange = 0;
        long totalUninvested = 0;
        
        // 데이터가 없는 달에 대한 방어 코드 (빈 리스트로 처리됨)
        Map<PaymentCategory, Long> categorySpendingMap = events.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        PaymentEvent::getCategory,
                        Collectors.summingLong(PaymentEvent::getAmount)
                ));

        for (PaymentEvent event : events) {
            totalPayment += event.getAmount();

            if (event.getStatus() == PaymentStatus.INVESTED) {
                totalInvestedChange += event.getSpareChange();
            } else if (event.getStatus() == PaymentStatus.NOT_INVESTED || event.getStatus() == PaymentStatus.FAILED) {
                totalUninvested += event.getSpareChange();
            }
        }

        List<PaymentDashboardResponse.CategorySpending> categorySpending = new ArrayList<>();
        for (Map.Entry<PaymentCategory, Long> entry : categorySpendingMap.entrySet()) {
            categorySpending.add(new PaymentDashboardResponse.CategorySpending(entry.getKey().name(), entry.getValue()));
        }

        return new PaymentDashboardResponse(
                totalPayment,
                totalInvestedChange,
                totalUninvested,
                pendingCount,
                categorySpending
        );
    }

    @Transactional(readOnly = true)
    public Slice<PaymentHistoryResponse> getHistoryFeed(String email, String status, String month, Pageable pageable) {
        User user = getUserByEmail(email);
        Long userId = user.getId();

        YearMonth yearMonth = parseMonth(month);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);

        List<PaymentStatus> mappedStatuses = mapStatusFilter(status);

        Slice<PaymentEvent> events = paymentEventRepository.findHistoryFeed(userId, startOfMonth, endOfMonth, mappedStatuses, pageable);

        return events.map(this::mapToResponse);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (DateTimeParseException e) {
            throw new InvalidInputValueException(); // 잘못된 날짜 형식
        }
    }

    private List<PaymentStatus> mapStatusFilter(String status) {
        if (status == null || status.equalsIgnoreCase("ALL")) {
            return List.of(
                    PaymentStatus.PENDING_PURCHASE,
                    PaymentStatus.INVESTED,
                    PaymentStatus.NOT_INVESTED,
                    PaymentStatus.FAILED
            );
        }

        return switch (status.toUpperCase()) {
            case "PENDING" -> List.of(PaymentStatus.PENDING_PURCHASE);
            case "INVESTED" -> List.of(PaymentStatus.INVESTED);
            case "CANCELED" -> List.of(PaymentStatus.NOT_INVESTED, PaymentStatus.FAILED);
            default -> throw new InvalidInputValueException(); // 잘못된 상태 문자열
        };
    }

    private PaymentHistoryResponse mapToResponse(PaymentEvent event) {
        String statusStr;
        LocalDateTime expiredAt = null;

        if (event.getStatus() == PaymentStatus.PENDING_PURCHASE) {
            statusStr = "PENDING";
            expiredAt = event.getCreatedAt().plusHours(24);
        } else if (event.getStatus() == PaymentStatus.INVESTED) {
            statusStr = "INVESTED";
        } else if (event.getStatus() == PaymentStatus.NOT_INVESTED || event.getStatus() == PaymentStatus.FAILED) {
            statusStr = "CANCELED";
        } else {
            // 혹시라도 과도기 상태가 들어오면 PENDING으로 매핑
            statusStr = "PENDING";
        }

        String targetCoinMarket = event.getTargetCoin() != null ? event.getTargetCoin().getMarket() : null;
        String targetCoinName = event.getTargetCoin() != null ? event.getTargetCoin().getKoreanName() : null;

        return new PaymentHistoryResponse(
                event.getId(),
                event.getMerchant(),
                event.getAmount(),
                event.getSpareChange(),
                event.getCategory() != null ? event.getCategory().name() : null,
                statusStr,
                expiredAt,
                targetCoinMarket,
                targetCoinName,
                event.getCreatedAt()
        );
    }
}