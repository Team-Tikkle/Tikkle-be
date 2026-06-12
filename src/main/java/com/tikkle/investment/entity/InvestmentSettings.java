package com.tikkle.investment.entity;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "investment_settings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
}