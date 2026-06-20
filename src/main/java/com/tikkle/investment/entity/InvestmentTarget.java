package com.tikkle.investment.entity;

import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INVESTMENT_TARGETS", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_target_date", columnNames = {"user_id", "target_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String stockName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private LocalDate targetDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private InvestmentTarget(User user, String ticker, String stockName, String reason, LocalDate targetDate) {
        this.user = user;
        this.ticker = ticker;
        this.stockName = stockName;
        this.reason = reason;
        this.targetDate = targetDate;
    }

    public void updateTarget(String ticker, String stockName, String reason) {
        this.ticker = ticker;
        this.stockName = stockName;
        this.reason = reason;
    }
}