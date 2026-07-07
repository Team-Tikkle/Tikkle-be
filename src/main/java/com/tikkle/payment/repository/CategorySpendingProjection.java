package com.tikkle.payment.repository;

import com.tikkle.payment.entity.enums.PaymentCategory;

public interface CategorySpendingProjection {
    PaymentCategory getCategory();
    Long getAmount();
}