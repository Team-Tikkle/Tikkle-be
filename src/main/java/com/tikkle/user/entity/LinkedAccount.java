package com.tikkle.user.entity;

import com.tikkle.global.util.AES256Converter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "linked_accounts")
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
    @Column(nullable = false, length = 512)
    private String upbitAccessKey;

    @Convert(converter = AES256Converter.class)
    @Column(nullable = false, length = 512)
    private String upbitSecretKey;

    @Column(nullable = false, length = 50)
    private String targetCardCompany;

    @Column(nullable = false, length = 4)
    private String targetCardLast4;

    @Builder
    private LinkedAccount(User user, String upbitAccessKey, String upbitSecretKey, String targetCardCompany, String targetCardLast4) {
        this.user = user;
        this.upbitAccessKey = upbitAccessKey;
        this.upbitSecretKey = upbitSecretKey;
        this.targetCardCompany = targetCardCompany;
        this.targetCardLast4 = targetCardLast4;
    }

    public void updateUpbitCredentials(String upbitAccessKey, String upbitSecretKey) {
        this.upbitAccessKey = upbitAccessKey;
        this.upbitSecretKey = upbitSecretKey;
    }
}