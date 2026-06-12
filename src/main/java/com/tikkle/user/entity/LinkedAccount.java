package com.tikkle.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "linked_accounts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LinkedAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // AES-256 양방향 암호화가 적용된 상태로 적재
    @Column(nullable = false)
    private String kisAppKey;

    @Column(nullable = false)
    private String kisAppSecret;

    @Column(nullable = false)
    private String kisAccountNum;

    @Column(nullable = false, length = 50)
    private String targetCardCompany;

    @Column(nullable = false, length = 4)
    private String targetCardLast4;
}