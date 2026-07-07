package com.tikkle.payment.entity;

import com.tikkle.payment.entity.enums.PaymentCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "PAYMENT_CATEGORY_MAPPING")
public class PaymentCategoryMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentCategory category;

    @Builder
    public PaymentCategoryMapping(String keyword, PaymentCategory category) {
        this.keyword = keyword;
        this.category = category;
    }
}