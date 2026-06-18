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
    private String kisAppKey;

    @Convert(converter = AES256Converter.class)
    @Column(nullable = false, length = 512)
    private String kisAppSecret;

    @Convert(converter = AES256Converter.class)
    @Column(nullable = false, length = 512)
    private String kisAccountNum;

    @Column(nullable = false, length = 50)
    private String targetCardCompany;

    @Column(nullable = false, length = 4)
    private String targetCardLast4;

    @Builder
    private LinkedAccount(User user, String kisAppKey, String kisAppSecret, String kisAccountNum, String targetCardCompany, String targetCardLast4) {
        this.user = user;
        this.kisAppKey = kisAppKey;
        this.kisAppSecret = kisAppSecret;
        this.kisAccountNum = kisAccountNum;
        this.targetCardCompany = targetCardCompany;
        this.targetCardLast4 = targetCardLast4;
    }

    public void updateKisCredentials(String kisAppKey, String kisAppSecret, String kisAccountNum) {
        this.kisAppKey = kisAppKey;
        this.kisAppSecret = kisAppSecret;
        this.kisAccountNum = kisAccountNum;
    }
}