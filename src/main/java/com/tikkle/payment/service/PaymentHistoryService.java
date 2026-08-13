package com.tikkle.payment.service;


import com.tikkle.global.exception.InvalidInputValueException;
import com.tikkle.payment.dto.response.InProgressPaymentResponse;
import com.tikkle.payment.dto.response.PaymentDashboardResponse;
import com.tikkle.payment.dto.response.PaymentHistoryResponse;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.exception.PaymentEventNotFoundException;
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

    /**
     * 특정 월의 결제 대시보드 통계(누적 결제액, 누적 투자액, 카테고리별 소비 등)를 집계하여 반환합니다.
     *
     * @param userId 사용자 ID
     * @param month 조회할 월(yyyy-MM 형식)
     * @return 대시보드 통계 결과 DTO
     */
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

        Long sumInvested = paymentEventRepository.sumSpareChangeByUserIdAndStatusesAndCreatedAtBetween(userId, List.of(PaymentStatus.INVESTED), startOfMonth, endOfMonth);
        long totalInvestedChange = sumInvested != null ? sumInvested : 0L;

        Long sumUninvested = paymentEventRepository.sumSpareChangeByUserIdAndStatusesAndCreatedAtBetween(userId, List.of(PaymentStatus.NOT_INVESTED, PaymentStatus.FAILED), startOfMonth, endOfMonth);
        long totalUninvested = sumUninvested != null ? sumUninvested : 0L;

        List<CategorySpendingProjection> categoryProjections = paymentEventRepository.findCategorySpendingByUserIdAndCreatedAtBetween(userId, startOfMonth, endOfMonth);
        List<PaymentDashboardResponse.CategorySpending> categorySpending = categoryProjections.stream()
                .map(p -> new PaymentDashboardResponse.CategorySpending(p.getCategory().name(), p.getAmount()))
                .collect(Collectors.toList());

        // 3. 리스너 유실 가시화용 — 가장 최근 결제 감지 시각(월 무관)
        LocalDateTime lastPaymentDetectedAt = paymentEventRepository.findLatestCreatedAtByUserId(userId);

        return new PaymentDashboardResponse(
                totalPayment,
                totalInvestedChange,
                totalUninvested,
                pendingCount,
                lastPaymentDetectedAt,
                categorySpending
        );
    }

    /**
     * 결제 내역 및 투자 상태를 포함한 피드를 무한 스크롤 페이징 형태로 조회합니다.
     *
     * @param userId 사용자 ID
     * @param status 필터링할 결제 상태 (ALL, PENDING, INVESTED, CANCELED)
     * @param month 조회할 월(yyyy-MM 형식)
     * @param pageable 페이징 정보
     * @return 결제 내역 피드 결과(Slice)
     */
    @Transactional(readOnly = true)
    public Slice<PaymentHistoryResponse> getHistoryFeed(Long userId, String status, String month, Pageable pageable) {
        YearMonth yearMonth = parseMonth(month);
        LocalDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(LocalTime.MAX);

        List<PaymentStatus> mappedStatuses = mapStatusFilter(status);

        Slice<PaymentEvent> events = paymentEventRepository.findHistoryFeed(userId, startOfMonth, endOfMonth, mappedStatuses, pageable);

        return events.map(PaymentHistoryResponse::from);
    }

    /**
     * 매수 승인 이후 아직 끝나지 않은 결제 건을 조회합니다.
     * 2차 인증을 위해 앱을 벗어났다 돌아온 사용자가 진행 중인 건의 화면을 복구하고
     * 해당 eventId로 SSE를 재구독할 수 있게 하는 용도입니다.
     *
     * @param userId 사용자 ID
     * @return 진행 중인 결제 건 목록 (최신순, 없으면 빈 목록)
     */
    @Transactional(readOnly = true)
    public List<InProgressPaymentResponse> getInProgressPayments(Long userId) {
        List<PaymentEvent> events = paymentEventRepository.findInProgress(
                userId, List.of(PaymentStatus.PENDING_DEPOSIT, PaymentStatus.PENDING_TRADE));

        return events.stream()
                .map(InProgressPaymentResponse::from)
                .toList();
    }

    /**
     * 특정 결제 건의 카테고리를 사용자가 직접 변경합니다.
     *
     * @param userId 사용자 ID
     * @param paymentId 결제 이벤트 ID
     * @param category 변경할 카테고리
     */
    @Transactional
    public void updateCategory(Long userId, Long paymentId, PaymentCategory category) {
        log.info("[PaymentHistoryService] 결제 카테고리 변경 처리 - userId: {}, paymentId: {}, category: {}", userId, paymentId, category);
        PaymentEvent event = paymentEventRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(PaymentEventNotFoundException::new);

        event.updateCategory(category);
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
                    PaymentStatus.PENDING_DEPOSIT,
                    PaymentStatus.PENDING_TRADE,
                    PaymentStatus.INVESTED,
                    PaymentStatus.NOT_INVESTED,
                    PaymentStatus.FAILED
            );
        }

        return switch (status.toUpperCase()) {
            case "PENDING" -> List.of(PaymentStatus.PENDING_PURCHASE, PaymentStatus.PENDING_DEPOSIT, PaymentStatus.PENDING_TRADE);
            case "INVESTED" -> List.of(PaymentStatus.INVESTED);
            case "CANCELED" -> List.of(PaymentStatus.NOT_INVESTED, PaymentStatus.FAILED);
            default -> throw new InvalidInputValueException();
        };
    }
}