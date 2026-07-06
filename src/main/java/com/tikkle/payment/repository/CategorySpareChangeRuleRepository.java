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

    /**
     * 결제 파이프라인에서 특정 카테고리의 룰 1개만 빠르게 찾을 때 사용합니다.
     *
     * @param userId 사용자 ID
     * @param category 결제 카테고리
     * @return 카테고리별 잔돈 규칙 (Optional)
     */
    Optional<CategorySpareChangeRule> findByUserIdAndCategory(Long userId, PaymentCategory category);

    /**
     * 특정 카테고리 룰이 없을 때, 유저가 설정한 '기본 룰(category가 null인 값)'을 찾을 때 사용합니다.
     *
     * @param userId 사용자 ID
     * @return 기본 잔돈 규칙 (Optional)
     */
    @Query("SELECT r FROM CategorySpareChangeRule r WHERE r.user.id = :userId AND r.category IS NULL")
    Optional<CategorySpareChangeRule> findDefaultByUserId(@Param("userId") Long userId);
}