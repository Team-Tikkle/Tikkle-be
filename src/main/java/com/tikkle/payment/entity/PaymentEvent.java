package com.tikkle.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(name = "card_company", nullable = false, length = 50)
    private String cardCompany;

    @Column(name = "card_number_last_4", nullable = false, length = 4)
    private String cardNumberLast4;

    @Column(name = "merchant", nullable = false, length = 100)
    private String merchant;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "spare_change", nullable = false)
    private Integer spareChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    @Column(name = "reason")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PaymentEvent(Long userId, String cardCompany, String cardNumberLast4, String merchant, Integer amount, Integer spareChange, String transactionId, PaymentStatus status, String reason) {
        this.userId = userId;
        this.cardCompany = cardCompany;
        this.cardNumberLast4 = cardNumberLast4;
        this.merchant = merchant;
        this.amount = amount;
        this.spareChange = spareChange;
        this.transactionId = transactionId;
        this.status = status;
        this.reason = reason;
    }

    public void startClassifying() {
        this.status = PaymentStatus.CLASSIFYING;
    }

    public void completeInvestment() {
        this.status = PaymentStatus.INVESTED;
    }

    public void skipInvestment(String reason) {
        this.status = PaymentStatus.NOT_INVESTED;
        this.reason = reason;
    }
}