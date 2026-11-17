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
    // reason 컬럼의 길이 (길이를 지정하지 않은 문자열 컬럼의 기본값)
    private static final int REASON_MAX_LENGTH = 255;

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

    @Column(name = "raw_merchant", nullable = false, length = 100)
    private String rawMerchant;

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

    @Column(name = "deposit_uuid", unique = true)
    private String depositUuid;

    @Column(name = "deposit_requested_at")
    private LocalDateTime depositRequestedAt;

    @Column(name = "trade_uuid", unique = true)
    private String tradeUuid;

    @Column(name = "trade_requested_at")
    private LocalDateTime tradeRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_coin_market")
    private Coin targetCoin;

    @Column(name = "invested_volume", precision = 30, scale = 8)
    private BigDecimal investedVolume;

    @Column(name = "invested_price", precision = 30, scale = 8)
    private BigDecimal investedPrice;

    @Builder
    public PaymentEvent(Long userId, String cardCompany, String cardNumberLast4, String merchant, String rawMerchant, Integer amount, Integer spareChange, PaymentCategory category, String transactionId, PaymentStatus status, String reason, Coin targetCoin) {
        this.userId = userId;
        this.cardCompany = cardCompany;
        this.cardNumberLast4 = cardNumberLast4;
        this.merchant = merchant;
        this.rawMerchant = rawMerchant;
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
        this.reason = null;
    }

    public void skipInvestment(String reason) {
        this.status = PaymentStatus.NOT_INVESTED;
        this.reason = fitReasonLength(reason);
    }

    public void failInvestment(String reason) {
        this.status = PaymentStatus.FAILED;
        this.reason = fitReasonLength(reason);
    }

    public void revertToPendingPurchase(String reason) {
        this.status = PaymentStatus.PENDING_PURCHASE;
        this.reason = fitReasonLength(reason);
        this.depositUuid = null;
        this.depositRequestedAt = null;
    }

    // 사유에 예외 메시지가 붙으면 컬럼 길이를 넘을 수 있다. 넘으면 UPDATE 자체가 실패해 상태를 기록하지 못하므로 잘라서 저장한다
    private static String fitReasonLength(String reason) {
        if (reason == null || reason.length() <= REASON_MAX_LENGTH) {
            return reason;
        }
        int end = REASON_MAX_LENGTH;
        // 이모지 같은 2칸 문자의 앞쪽만 남으면 저장할 수 없는 문자가 되므로 한 칸 더 줄인다
        if (Character.isHighSurrogate(reason.charAt(end - 1))) {
            end--;
        }
        return reason.substring(0, end);
    }

    public void updateCategory(PaymentCategory category) {
        this.category = category;
    }

    public void updateToPendingDeposit(String depositUuid) {
        this.status = PaymentStatus.PENDING_DEPOSIT;
        this.depositUuid = depositUuid;
        this.depositRequestedAt = LocalDateTime.now();
    }

    public void updateToPendingTrade(String tradeUuid) {
        this.status = PaymentStatus.PENDING_TRADE;
        this.tradeUuid = tradeUuid;
        this.tradeRequestedAt = LocalDateTime.now();
        this.reason = null;
    }
}