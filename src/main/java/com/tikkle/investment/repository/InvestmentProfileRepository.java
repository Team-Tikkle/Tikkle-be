package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InvestmentProfileRepository extends JpaRepository<InvestmentProfile, Long> {
    Optional<InvestmentProfile> findByUserId(Long userId);

    @Query("SELECT DISTINCT p FROM InvestmentProfile p LEFT JOIN FETCH p.cryptoThemes")
    List<InvestmentProfile> findAllWithThemes();
}