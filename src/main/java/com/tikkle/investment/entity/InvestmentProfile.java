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
@Table(name = "investment_profiles")
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
    private ReturnPreference firstReturnPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnPreference secondReturnPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnPreference thirdReturnPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MarketPreference marketPreference;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "investment_profile_themes",
            joinColumns = @JoinColumn(name = "investment_profile_id")
    )
    @Column(name = "preferred_theme", nullable = false)
    @Enumerated(EnumType.STRING)
    private List<PreferredTheme> preferredThemes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "investment_profile_value_filters",
            joinColumns = @JoinColumn(name = "investment_profile_id")
    )
    @Column(name = "value_filter", nullable = false)
    @Enumerated(EnumType.STRING)
    private List<ValueFilter> valueFilters = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DiversificationType diversificationType;

    @Builder
    private InvestmentProfile(User user,
                              ReturnPreference firstReturnPreference,
                              ReturnPreference secondReturnPreference,
                              ReturnPreference thirdReturnPreference,
                              MarketPreference marketPreference,
                              List<PreferredTheme> preferredThemes,
                              List<ValueFilter> valueFilters,
                              DiversificationType diversificationType) {
        this.user = user;
        this.firstReturnPreference = firstReturnPreference;
        this.secondReturnPreference = secondReturnPreference;
        this.thirdReturnPreference = thirdReturnPreference;
        this.marketPreference = marketPreference;
        this.preferredThemes = preferredThemes != null ? new ArrayList<>(preferredThemes) : new ArrayList<>();
        this.valueFilters = valueFilters != null ? new ArrayList<>(valueFilters) : new ArrayList<>();
        this.diversificationType = diversificationType;
    }
}