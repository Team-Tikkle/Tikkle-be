package com.tikkle.payment.service.component;

import com.tikkle.payment.entity.enums.RuleType;
import org.springframework.stereotype.Component;

@Component
public class SpareChangeCalculator {
    public int calculate(int amount, RuleType ruleType) {
        if (amount <= 0) {
            return 0;
        }

        return switch (ruleType) {
            case ROUND_UP_10000 -> (10000 - (amount % 10000)) % 10000;
            case ROUND_UP_20000 -> (20000 - (amount % 20000)) % 20000;
            case ROUND_UP_30000 -> (30000 - (amount % 30000)) % 30000;
            case ROUND_UP_40000 -> (40000 - (amount % 40000)) % 40000;
            case ROUND_UP_50000 -> (50000 - (amount % 50000)) % 50000;
            case PERCENT_10 -> java.math.BigDecimal.valueOf(amount).multiply(new java.math.BigDecimal("0.10")).intValue();
            case PERCENT_15 -> java.math.BigDecimal.valueOf(amount).multiply(new java.math.BigDecimal("0.15")).intValue();
            case PERCENT_20 -> java.math.BigDecimal.valueOf(amount).multiply(new java.math.BigDecimal("0.20")).intValue();
            case PERCENT_25 -> java.math.BigDecimal.valueOf(amount).multiply(new java.math.BigDecimal("0.25")).intValue();
            case PERCENT_30 -> java.math.BigDecimal.valueOf(amount).multiply(new java.math.BigDecimal("0.30")).intValue();
        };
    }
}