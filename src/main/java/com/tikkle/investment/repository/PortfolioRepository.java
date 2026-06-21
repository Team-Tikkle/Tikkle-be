package com.tikkle.investment.repository;

import com.tikkle.investment.entity.Portfolio;
import com.tikkle.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserId(Long userId);
    List<Portfolio> findByUserIdIn(List<Long> userIds);
    Optional<Portfolio> findByUserAndMarket(User user, String market);
    Optional<Portfolio> findByUserIdAndMarket(Long userId, String market);
}