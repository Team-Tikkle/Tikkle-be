package com.tikkle.investment.entity;

import com.tikkle.investment.entity.enums.OrderStatus;
import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "INVESTMENT_ORDERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String ticker;

    @Column(nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private InvestmentOrder(User user, String ticker, int totalAmount, OrderStatus status) {
        this.user = user;
        this.ticker = ticker;
        this.totalAmount = totalAmount;
        this.status = status != null ? status : OrderStatus.PENDING;
    }

    public void markExecuted() {
        this.status = OrderStatus.EXECUTED;
    }

    public void markFailed() {
        this.status = OrderStatus.FAILED;
    }
}