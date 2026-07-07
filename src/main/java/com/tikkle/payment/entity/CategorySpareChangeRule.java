package com.tikkle.payment.entity;

import com.tikkle.user.entity.User;
import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CATEGORY_SPARE_CHANGE_RULES",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "category"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class
CategorySpareChangeRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleType ruleType;

    @Builder
    private CategorySpareChangeRule(User user, PaymentCategory category, RuleType ruleType) {
        this.user = user;
        this.category = category;
        this.ruleType = ruleType;
    }

    public void change(RuleType ruleType) {
        this.ruleType = ruleType;
    }
}