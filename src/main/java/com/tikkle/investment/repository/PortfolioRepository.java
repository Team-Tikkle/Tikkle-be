package com.tikkle.investment.repository;

import com.tikkle.investment.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserId(Long userId);
    List<Portfolio> findByUserIdIn(List<Long> userIds);
}