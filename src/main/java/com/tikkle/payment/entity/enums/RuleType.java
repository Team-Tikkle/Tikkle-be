package com.tikkle.payment.entity.enums;

public enum RuleType {
    NONE,            // 잔돈 미적립 (카테고리 오프)
    ROUND_UP_1000,   // 1,000원 올림
    ROUND_UP_5000,
    ROUND_UP_10000,
    ROUND_UP_50000,
    PERCENT_5,       // 결제액의 5%
    PERCENT_10,
    PERCENT_20,
    PERCENT_30
}