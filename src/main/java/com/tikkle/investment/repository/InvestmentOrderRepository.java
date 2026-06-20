package com.tikkle.investment.repository;

import com.tikkle.investment.entity.InvestmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentOrderRepository extends JpaRepository<InvestmentOrder, Long> {
}