package com.tikkle.payment.repository;

import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategorySpareChangeRuleRepository extends JpaRepository<CategorySpareChangeRule, Long> {
    List<CategorySpareChangeRule> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}