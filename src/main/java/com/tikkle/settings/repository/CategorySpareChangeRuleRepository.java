package com.tikkle.settings.repository;

import com.tikkle.settings.entity.CategorySpareChangeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorySpareChangeRuleRepository extends JpaRepository<CategorySpareChangeRule, Long> {
    List<CategorySpareChangeRule> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}