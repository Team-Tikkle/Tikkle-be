package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface InvestmentTargetRepository extends JpaRepository<InvestmentTarget, Long> {
    Optional<InvestmentTarget> findByUserIdAndTargetDate(Long userId, LocalDate targetDate);
}