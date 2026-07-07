package com.tikkle.payment.entity;

import com.tikkle.investment.entity.Coin;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.math.BigDecimal;

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
    @Column(name = "category", length = 20)
    private PaymentCategory category;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_coin_market")
    private Coin targetCoin;

    @Column(name = "invested_volume", precision = 30, scale = 8)
    private BigDecimal investedVolume;

    @Column(name = "invested_price", precision = 30, scale = 8)
    private BigDecimal investedPrice;

    @Builder
    public PaymentEvent(Long userId, String cardCompany, String cardNumberLast4, String merchant, Integer amount, Integer spareChange, PaymentCategory category, String transactionId, PaymentStatus status, String reason, Coin targetCoin) {
        this.userId = userId;
        this.cardCompany = cardCompany;
        this.cardNumberLast4 = cardNumberLast4;
        this.merchant = merchant;
        this.amount = amount;
        this.spareChange = spareChange;
        this.category = category;
        this.transactionId = transactionId;
        this.status = status;
        this.reason = reason;
        this.targetCoin = targetCoin;
    }

    public void completeInvestment(BigDecimal volume, BigDecimal price) {
        this.status = PaymentStatus.INVESTED;
        this.investedVolume = volume;
        this.investedPrice = price;
    }

    public void skipInvestment(String reason) {
        this.status = PaymentStatus.NOT_INVESTED;
        this.reason = reason;
    }

    public void failInvestment(String reason) {
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
    }
}