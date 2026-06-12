package com.tikkle.investment.entity;

import com.tikkle.investment.entity.enums.*;
import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "investment_profiles")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InvestmentProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RiskTolerance riskTolerance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvestmentTerm investmentTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvestmentStyle investmentStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PreferredTheme preferredTheme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockCapPreference stockCapPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MarketPreference marketPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EsgFocus esgFocus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SinIndustryFilter sinIndustryFilter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnPreference returnPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiversificationType diversificationType;
}