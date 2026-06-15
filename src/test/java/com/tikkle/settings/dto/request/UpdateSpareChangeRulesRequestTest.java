package com.tikkle.settings.dto.request;

import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.payment.entity.enums.RuleType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSpareChangeRulesRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("중복 카테고리가 있으면 검증에 실패한다")
    void rejectsDuplicateCategories() {
        UpdateSpareChangeRulesRequest request = new UpdateSpareChangeRulesRequest(List.of(
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.CAFE, RuleType.PERCENT_5),
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.CAFE, RuleType.NONE)));

        Set<ConstraintViolation<UpdateSpareChangeRulesRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("동일한 결제 카테고리는 중복될 수 없습니다.");
    }

    @Test
    @DisplayName("카테고리가 모두 고유하면 검증을 통과한다")
    void passesWhenCategoriesUnique() {
        UpdateSpareChangeRulesRequest request = new UpdateSpareChangeRulesRequest(List.of(
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.CAFE, RuleType.PERCENT_5),
                new UpdateSpareChangeRulesRequest.RuleItem(PaymentCategory.SHOPPING, RuleType.NONE)));

        Set<ConstraintViolation<UpdateSpareChangeRulesRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
