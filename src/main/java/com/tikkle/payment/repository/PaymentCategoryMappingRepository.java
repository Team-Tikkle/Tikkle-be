package com.tikkle.payment.repository;

import com.tikkle.payment.entity.PaymentCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentCategoryMappingRepository extends JpaRepository<PaymentCategoryMapping, Long> {
    @Query("SELECT m FROM PaymentCategoryMapping m WHERE :merchantName LIKE CONCAT('%', m.keyword, '%') ORDER BY LENGTH(m.keyword) DESC")
    List<PaymentCategoryMapping> findByKeywordContaining(@Param("merchantName") String merchantName);
}