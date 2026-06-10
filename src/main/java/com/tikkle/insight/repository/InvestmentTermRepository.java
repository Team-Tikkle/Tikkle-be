package com.tikkle.insight.repository;

import com.tikkle.insight.entity.InvestmentTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentTermRepository extends JpaRepository<InvestmentTerm, Long> {
    List<InvestmentTerm> findAllByOrderByDisplayOrderAsc();
}