package com.tikkle.investment.entity;

import com.tikkle.investment.entity.enums.*;
import com.tikkle.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "INVESTMENT_PROFILES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private TrendSensitivity trendSensitivity;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "INVESTMENT_PROFILE_THEMES",
            joinColumns = @JoinColumn(name = "investment_profile_id")
    )
    @Column(name = "theme", nullable = false)
    @Enumerated(EnumType.STRING)
    private List<CryptoTheme> cryptoThemes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiversificationType diversificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemeAcceptance memeAcceptance;

    @Builder
    private InvestmentProfile(User user,
                              RiskTolerance riskTolerance,
                              TrendSensitivity trendSensitivity,
                              List<CryptoTheme> cryptoThemes,
                              DiversificationType diversificationType,
                              MemeAcceptance memeAcceptance) {
        this.user = user;
        this.riskTolerance = riskTolerance;
        this.trendSensitivity = trendSensitivity;
        this.cryptoThemes = cryptoThemes != null ? new ArrayList<>(cryptoThemes) : new ArrayList<>();
        this.diversificationType = diversificationType;
        this.memeAcceptance = memeAcceptance;
    }
}