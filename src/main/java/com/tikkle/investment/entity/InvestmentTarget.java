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

    @Column(nullable = false, length = 20)
    private String market;

    @Column(nullable = false, length = 100)
    private String coinName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private LocalDate targetDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private InvestmentTarget(User user, String market, String coinName, String reason, LocalDate targetDate) {
        this.user = user;
        this.market = market;
        this.coinName = coinName;
        this.reason = reason;
        this.targetDate = targetDate;
    }

    public void updateTarget(String market, String coinName, String reason) {
        this.market = market;
        this.coinName = coinName;
        this.reason = reason;
    }
}