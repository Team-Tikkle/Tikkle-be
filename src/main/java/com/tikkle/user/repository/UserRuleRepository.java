package com.tikkle.user.repository;

import com.tikkle.payment.entity.enums.PaymentCategory;
import com.tikkle.user.entity.UserRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRuleRepository extends JpaRepository<UserRule, Long> {
    Optional<UserRule> findByUserIdAndCategory(Long userId, PaymentCategory category);

    @Query("SELECT ur FROM UserRule ur WHERE ur.user.id = :userId AND ur.category IS NULL")
    Optional<UserRule> findDefaultByUserId(@Param("userId") Long userId);
}