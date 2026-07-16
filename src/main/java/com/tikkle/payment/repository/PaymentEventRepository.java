package com.tikkle.payment.repository;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByIdAndUserId(Long id, Long userId);

    // 회원 탈퇴 시 원장 일괄 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaymentEvent p WHERE p.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEvent p WHERE p.id = :id AND p.userId = :userId")
    Optional<PaymentEvent> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    List<PaymentEvent> findByStatus(PaymentStatus status);

    List<PaymentEvent> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime dateTime);

    long countByUserIdAndStatus(Long userId, PaymentStatus status);

    @Query("SELECT SUM(p.amount) FROM PaymentEvent p WHERE p.userId = :userId AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    Long sumAmountByUserIdAndCreatedAtBetween(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(p.spareChange) FROM PaymentEvent p WHERE p.userId = :userId AND p.status IN :statuses AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    Long sumSpareChangeByUserIdAndStatusesAndCreatedAtBetween(@Param("userId") Long userId, @Param("statuses") List<PaymentStatus> statuses, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p.category as category, SUM(p.amount) as amount FROM PaymentEvent p WHERE p.userId = :userId AND p.createdAt >= :startDate AND p.createdAt <= :endDate AND p.category IS NOT NULL GROUP BY p.category")
    List<CategorySpendingProjection> findCategorySpendingByUserIdAndCreatedAtBetween(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);


    @Query("SELECT p.targetCoin.market FROM PaymentEvent p WHERE p.userId = :userId AND p.status = com.tikkle.payment.entity.enums.PaymentStatus.INVESTED ORDER BY p.createdAt DESC")
    List<String> findRecentPurchasedMarkets(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"targetCoin"})
    @Query("SELECT p FROM PaymentEvent p " +
            "WHERE p.userId = :userId " +
            "AND p.createdAt >= :startDate AND p.createdAt <= :endDate " +
            "AND p.status IN :statuses " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    Slice<PaymentEvent> findHistoryFeed(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("statuses") List<PaymentStatus> statuses,
            Pageable pageable);
}