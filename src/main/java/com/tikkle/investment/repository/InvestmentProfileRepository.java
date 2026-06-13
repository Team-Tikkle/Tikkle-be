package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentProfileRepository extends JpaRepository<InvestmentProfile, Long> {
}