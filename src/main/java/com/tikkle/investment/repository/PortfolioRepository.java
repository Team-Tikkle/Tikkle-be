package com.tikkle.investment.repository;

import com.tikkle.investment.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserId(Long userId);
    Optional<Portfolio> findByUserIdAndMarket(Long userId, String market);
    void deleteByUserId(Long userId);
}