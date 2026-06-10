package com.tikkle.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "USERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "target_card_company", length = 50)
    private String targetCardCompany;

    @Column(name = "target_card_number_last_4", length = 4)
    private String targetCardNumberLast4;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime deletedAt;

    @Builder
    private User(String name, String email, AuthProvider provider, String providerId, UserStatus status, String targetCardCompany, String targetCardNumberLast4) {
        this.name = name;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.status = status;
        this.targetCardCompany = targetCardCompany;
        this.targetCardNumberLast4 = targetCardNumberLast4;
    }

    public void update(String name) {
        if (name != null) this.name = name;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }
}