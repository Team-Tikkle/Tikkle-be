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
            case ROUND_UP_1000 -> (1000 - (amount % 1000)) % 1000;
            case ROUND_UP_5000 -> (5000 - (amount % 5000)) % 5000;
            case ROUND_UP_10000 -> (10000 - (amount % 10000)) % 10000;
            case ROUND_UP_50000 -> (50000 - (amount % 50000)) % 50000;
            case PERCENT_5 -> (int) (amount * 0.05);
            case PERCENT_10 -> (int) (amount * 0.1);
            case PERCENT_20 -> (int) (amount * 0.2);
            case PERCENT_30 -> (int) (amount * 0.3);
        };
    }
}