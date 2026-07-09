package com.tikkle.investment.entity;

import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "PORTFOLIOS", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_market", columnNames = {"user_id", "market"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String market;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal averagePrice = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Portfolio(User user, String market, BigDecimal quantity, BigDecimal averagePrice) {
        this.user = user;
        this.market = market;
        this.quantity = quantity != null ? quantity : BigDecimal.ZERO;
        this.averagePrice = averagePrice != null ? averagePrice : BigDecimal.ZERO;
    }

    // 매수 체결 시 기존 보유 수량과 평균 매수 단가를 갱신합니다.
    // 가중 평균 공식: newAvg = (기존총액 + 신규총액) / (기존수량 + 신규수량)
    public void updateHolding(BigDecimal executedPrice, BigDecimal executedQuantity) {
        BigDecimal existingTotal = this.averagePrice.multiply(this.quantity);
        BigDecimal newTotal = executedPrice.multiply(executedQuantity);
        this.quantity = this.quantity.add(executedQuantity);
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            this.averagePrice = existingTotal.add(newTotal)
                    .divide(this.quantity, 4, RoundingMode.HALF_UP);
        }
    }

    // 매도 체결 시 기존 보유 수량을 차감합니다.
    public void sellHolding(BigDecimal executedQuantity) {
        if (this.quantity.compareTo(executedQuantity) < 0) {
            throw new IllegalArgumentException("매도 수량이 보유 수량을 초과할 수 없습니다.");
        }
        this.quantity = this.quantity.subtract(executedQuantity);
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.averagePrice = BigDecimal.ZERO;
        }
    }
}