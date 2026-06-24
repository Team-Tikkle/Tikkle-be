package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvestmentProfileRepository extends JpaRepository<InvestmentProfile, Long> {
    Optional<InvestmentProfile> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM InvestmentProfile p LEFT JOIN FETCH p.cryptoThemes")
    java.util.List<InvestmentProfile> findAllWithThemes();
}