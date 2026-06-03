package com.tikkle.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "PAYMENT_EVENTS")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "spare_change_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal spareChangeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PaymentEvent(Long userId, String merchantName, BigDecimal amount, BigDecimal spareChangeAmount) {
        this.userId = userId;
        this.merchantName = merchantName;
        this.amount = amount;
        this.spareChangeAmount = spareChangeAmount;
        this.status = PaymentStatus.PENDING;
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }
}