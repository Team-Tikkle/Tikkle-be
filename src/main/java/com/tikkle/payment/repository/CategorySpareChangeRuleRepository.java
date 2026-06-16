package com.tikkle.payment.repository;

import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategorySpareChangeRuleRepository extends JpaRepository<CategorySpareChangeRule, Long> {
    // 1. 온보딩이나 마이페이지에서 유저의 전체 룰을 가져올 때 사용
    List<CategorySpareChangeRule> findByUserId(Long userId);

    // 2. 결제 파이프라인에서 특정 카테고리의 룰 1개만 빠르게 찾을 때 사용
    Optional<CategorySpareChangeRule> findByUserIdAndCategory(Long userId, PaymentCategory category);

    // 3. 특정 카테고리 룰이 없을 때, 유저가 설정한 '기본 룰(category가 null인 값)'을 찾을 때 사용
    @Query("SELECT r FROM CategorySpareChangeRule r WHERE r.user.id = :userId AND r.category IS NULL")
    Optional<CategorySpareChangeRule> findDefaultByUserId(@Param("userId") Long userId);
}