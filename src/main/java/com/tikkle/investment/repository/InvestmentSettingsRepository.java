package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestmentSettingsRepository extends JpaRepository<InvestmentSettings, Long> {
    Optional<InvestmentSettings> findByUserId(Long userId);
}