package com.tikkle.payment.repository;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    boolean existsByTransactionId(String transactionId);
    java.util.Optional<PaymentEvent> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PaymentEvent p WHERE p.id = :id AND p.userId = :userId")
    java.util.Optional<PaymentEvent> findByIdAndUserIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("userId") Long userId);
    List<PaymentEvent> findByStatus(PaymentStatus status);
    List<PaymentEvent> findByIdIn(List<Long> ids);
    List<PaymentEvent> findByStatusAndCreatedAtBefore(PaymentStatus status, java.time.LocalDateTime dateTime);

    List<PaymentEvent> findByUserIdAndCreatedAtBetween(Long userId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    long countByUserIdAndStatus(Long userId, PaymentStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT p.targetCoin.id FROM PaymentEvent p WHERE p.userId = :userId AND p.status = 'INVESTED' ORDER BY p.createdAt DESC")
    List<String> findRecentPurchasedMarkets(@org.springframework.data.repository.query.Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"targetCoin"})
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PaymentEvent p " +
            "WHERE p.userId = :userId " +
            "AND p.createdAt >= :startDate AND p.createdAt <= :endDate " +
            "AND p.status IN :statuses " +
            "ORDER BY p.createdAt DESC, p.id DESC")
    org.springframework.data.domain.Slice<PaymentEvent> findHistoryFeed(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate,
            @org.springframework.data.repository.query.Param("statuses") List<PaymentStatus> statuses,
            org.springframework.data.domain.Pageable pageable);
}