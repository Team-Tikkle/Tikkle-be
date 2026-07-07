package com.tikkle.payment.service;


import com.tikkle.global.exception.InvalidInputValueException;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.CategorySpendingProjection;
import com.tikkle.payment.repository.PaymentEventRepository;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * 결제 내역 피드 및 대시보드 통계 비즈니스 로직을 처리하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentHistoryService {
    private final PaymentEventRepository paymentEventRepository;

    @Transactional(readOnly = true)
    public PaymentDashboardResponse getDashboard(Long userId, String month) {

        YearMonth yearMonth = parseMonth(month);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);

        // 1. 전체 누적 대기 건수 카운트
        long pendingCount = paymentEventRepository.countByUserIdAndStatus(userId, PaymentStatus.PENDING_PURCHASE);

        // 2. DB 집계 쿼리를 통한 통계 도출 (메모리 집계 최적화)
        Long sumAmount = paymentEventRepository.sumAmountByUserIdAndCreatedAtBetween(userId, startOfMonth, endOfMonth);
        long totalPayment = sumAmount != null ? sumAmount : 0L;

        Long sumInvested = paymentEventRepository.sumSpareChangeByUserIdAndStatusAndCreatedAtBetween(userId, PaymentStatus.INVESTED, startOfMonth, endOfMonth);
        long totalInvestedChange = sumInvested != null ? sumInvested : 0L;

        Long sumUninvested = paymentEventRepository.sumSpareChangeByUserIdAndStatusesAndCreatedAtBetween(userId, List.of(PaymentStatus.NOT_INVESTED, PaymentStatus.FAILED), startOfMonth, endOfMonth);
        long totalUninvested = sumUninvested != null ? sumUninvested : 0L;

        List<CategorySpendingProjection> categoryProjections = paymentEventRepository.findCategorySpendingByUserIdAndCreatedAtBetween(userId, startOfMonth, endOfMonth);
        List<PaymentDashboardResponse.CategorySpending> categorySpending = categoryProjections.stream()
                .map(p -> new PaymentDashboardResponse.CategorySpending(p.getCategory().name(), p.getAmount()))
                .collect(Collectors.toList());

        return new PaymentDashboardResponse(
                totalPayment,
                totalInvestedChange,
                totalUninvested,
                pendingCount,
                categorySpending
        );
    }

    @Transactional(readOnly = true)
    public Slice<PaymentHistoryResponse> getHistoryFeed(Long userId, String status, String month, Pageable pageable) {

        YearMonth yearMonth = parseMonth(month);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);

        List<PaymentStatus> mappedStatuses = mapStatusFilter(status);

        Slice<PaymentEvent> events = paymentEventRepository.findHistoryFeed(userId, startOfMonth, endOfMonth, mappedStatuses, pageable);

        return events.map(PaymentHistoryResponse::from);
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
            default -> throw new InvalidInputValueException();
        };
    }
}