package com.tikkle.payment.service.component;

import com.tikkle.payment.entity.enums.RuleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpareChangeCalculatorTest {

    private final SpareChangeCalculator calculator = new SpareChangeCalculator();

    @ParameterizedTest(name = "{0}: {1}원 결제 → 잔돈 {2}원")
    @DisplayName("올림 규칙은 결제 금액을 단위 금액의 배수로 올리는 데 필요한 차액을 잔돈으로 한다")
    @CsvSource({
            "ROUND_UP_10000, 4500, 5500",
            "ROUND_UP_10000, 9999, 1",
            "ROUND_UP_10000, 10001, 9999",
            "ROUND_UP_10000, 1, 9999",
            "ROUND_UP_20000, 4500, 15500",
            "ROUND_UP_20000, 19999, 1",
            "ROUND_UP_20000, 20001, 19999",
            "ROUND_UP_30000, 4500, 25500",
            "ROUND_UP_30000, 29999, 1",
            "ROUND_UP_30000, 30001, 29999",
            "ROUND_UP_40000, 4500, 35500",
            "ROUND_UP_40000, 39999, 1",
            "ROUND_UP_40000, 40001, 39999",
            "ROUND_UP_50000, 4500, 45500",
            "ROUND_UP_50000, 49999, 1",
            "ROUND_UP_50000, 50001, 49999",
    })
    void roundsUpToNextMultiple(RuleType ruleType, int amount, int expected) {
        assertThat(calculator.calculate(amount, ruleType)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}: {1}원 결제 → 잔돈 0원")
    @DisplayName("결제 금액이 단위 금액의 배수로 딱 떨어지면 잔돈은 0원이다")
    @CsvSource({
            "ROUND_UP_10000, 10000",
            "ROUND_UP_10000, 30000",
            "ROUND_UP_20000, 20000",
            "ROUND_UP_20000, 40000",
            "ROUND_UP_30000, 30000",
            "ROUND_UP_40000, 40000",
            "ROUND_UP_50000, 50000",
            "ROUND_UP_50000, 100000",
    })
    void returnsZeroForExactMultiple(RuleType ruleType, int amount) {
        assertThat(calculator.calculate(amount, ruleType)).isZero();
    }

    @ParameterizedTest(name = "{0}: {1}원 결제 → 잔돈 {2}원")
    @DisplayName("비율 규칙은 결제 금액의 정해진 비율을 잔돈으로 하고 원 단위 아래는 버린다")
    @CsvSource({
            "PERCENT_10, 12345, 1234",
            "PERCENT_15, 12345, 1851",
            "PERCENT_20, 12345, 2469",
            "PERCENT_25, 12345, 3086",
            "PERCENT_30, 12345, 3703",
            "PERCENT_10, 55000, 5500",
            "PERCENT_30, 17000, 5100",
            "PERCENT_10, 9, 0",
    })
    void takesPercentageAndTruncates(RuleType ruleType, int amount, int expected) {
        assertThat(calculator.calculate(amount, ruleType)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("결제 금액이 0원 이하이면 어떤 규칙이든 잔돈은 0원이다")
    @EnumSource(RuleType.class)
    void returnsZeroForNonPositiveAmount(RuleType ruleType) {
        assertThat(calculator.calculate(0, ruleType)).isZero();
        assertThat(calculator.calculate(-4500, ruleType)).isZero();
    }
}
