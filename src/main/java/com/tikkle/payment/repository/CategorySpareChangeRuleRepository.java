package com.tikkle.payment.repository;

import com.tikkle.payment.entity.CategorySpareChangeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorySpareChangeRuleRepository extends JpaRepository<CategorySpareChangeRule, Long> {
    List<CategorySpareChangeRule> findByUserId(Long userId);
}