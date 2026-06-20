package com.tikkle.payment.repository;

import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    boolean existsByTransactionId(String transactionId);
    List<PaymentEvent> findByStatus(PaymentStatus status);
    List<PaymentEvent> findByIdIn(List<Long> ids);
}