package com.tikkle.payment.service.component;

import com.tikkle.payment.entity.enums.RuleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 결제 금액과 유저가 설정한 잔돈 규칙(RuleType)을 바탕으로 실제 투자될 잔돈(SpareChange)을 계산하는 컴포넌트입니다.
 */
@Component
public class SpareChangeCalculator {
    /**
     * 금액과 잔돈 규칙을 받아 투자할 잔돈을 계산합니다.
     *
     * @param amount 결제 금액
     * @param ruleType 카테고리에 설정된 잔돈 규칙
     * @return 계산된 잔돈(원)
     */
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
            case PERCENT_10 -> BigDecimal.valueOf(amount).multiply(new BigDecimal("0.10")).intValue();
            case PERCENT_15 -> BigDecimal.valueOf(amount).multiply(new BigDecimal("0.15")).intValue();
            case PERCENT_20 -> BigDecimal.valueOf(amount).multiply(new BigDecimal("0.20")).intValue();
            case PERCENT_25 -> BigDecimal.valueOf(amount).multiply(new BigDecimal("0.25")).intValue();
            case PERCENT_30 -> BigDecimal.valueOf(amount).multiply(new BigDecimal("0.30")).intValue();
        };
    }
}