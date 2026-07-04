package com.tikkle.payment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.service.PaymentService;
import com.tikkle.payment.swagger.PaymentSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController implements PaymentSwagger {
    private final PaymentService paymentService;

    @Override
    @PostMapping
    public ApiResponse<?> receivePaymentScraping(@Valid @RequestBody PaymentScrapingRequest request) {
        log.info("Received payment scraping request for transaction ID: {}", request.transactionId());
        
        var response = paymentService.processPayment(request);
        
        return ApiResponse.success(response);
    }
}