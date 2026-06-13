package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentSettingsRepository extends JpaRepository<InvestmentSettings, Long> {
}