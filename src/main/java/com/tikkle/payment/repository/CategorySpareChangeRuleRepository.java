package com.tikkle.payment.repository;

import com.tikkle.payment.entity.CategorySpareChangeRule;
import com.tikkle.payment.entity.enums.PaymentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategorySpareChangeRuleRepository extends JpaRepository<CategorySpareChangeRule, Long> {
    /**
     * 온보딩이나 마이페이지에서 유저의 전체 룰을 가져올 때 사용합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자의 모든 카테고리 잔돈 규칙 목록
     */
    List<CategorySpareChangeRule> findByUserId(Long userId);
}