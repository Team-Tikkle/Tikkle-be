package com.tikkle.investment.entity;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "investment_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionMode executionMode;

    @Builder
    private InvestmentSettings(User user, ExecutionMode executionMode) {
        this.user = user;
        this.executionMode = executionMode;
    }
}