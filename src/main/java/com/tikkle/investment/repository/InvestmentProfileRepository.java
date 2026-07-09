package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestmentProfileRepository extends JpaRepository<InvestmentProfile, Long> {
    Optional<InvestmentProfile> findByUserId(Long userId);
}