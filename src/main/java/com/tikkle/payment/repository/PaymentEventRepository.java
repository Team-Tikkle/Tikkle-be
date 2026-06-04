package com.tikkle.payment.repository;

import com.tikkle.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}