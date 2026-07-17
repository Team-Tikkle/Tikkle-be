package com.tikkle.user.entity;

import com.tikkle.global.util.AES256Converter;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "LINKED_ACCOUNTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkedAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // AES-256 양방향 암호화가 적용된 상태로 적재
    @Convert(converter = AES256Converter.class)
    @Column(length = 512)
    private String upbitAccessKey;

    @Convert(converter = AES256Converter.class)
    @Column(length = 512)
    private String upbitSecretKey;

    @Column(length = 50)
    private String targetCardCompany;

    @Column(length = 4)
    private String targetCardLast4;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TwoFactorProvider twoFactorProvider;

    @Column(name = "is_investment_enabled", nullable = false)
    private boolean isInvestmentEnabled = true;

    @Builder
    private LinkedAccount(User user, String upbitAccessKey, String upbitSecretKey, String targetCardCompany, String targetCardLast4, TwoFactorProvider twoFactorProvider) {
        this.user = user;
        this.upbitAccessKey = upbitAccessKey;
        this.upbitSecretKey = upbitSecretKey;
        this.targetCardCompany = targetCardCompany;
        this.targetCardLast4 = targetCardLast4;
        this.twoFactorProvider = twoFactorProvider;
    }

    public void updateUpbitCredentials(String upbitAccessKey, String upbitSecretKey, TwoFactorProvider twoFactorProvider) {
        this.upbitAccessKey = upbitAccessKey;
        this.upbitSecretKey = upbitSecretKey;
        this.twoFactorProvider = twoFactorProvider;
    }

    public void updateKbankInfo(String targetCardCompany, String targetCardLast4) {
        this.targetCardCompany = targetCardCompany;
        this.targetCardLast4 = targetCardLast4;
    }

    public void updateInvestmentStatus(boolean isEnabled) {
        this.isInvestmentEnabled = isEnabled;
    }
}