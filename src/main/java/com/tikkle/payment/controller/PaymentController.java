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
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.tikkle.payment.exception.DuplicatePaymentException;
import com.tikkle.payment.dto.response.PaymentScrapingResponse;
import com.tikkle.payment.dto.response.PaymentActionType;

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

    @ExceptionHandler(DuplicatePaymentException.class)
    public ApiResponse<?> handleDuplicatePayment(DuplicatePaymentException e) {
        log.info("중복 결제 요청 처리 (200 OK 응답) - transactionId: {}", e.getRequest().transactionId());
        PaymentScrapingResponse response = new PaymentScrapingResponse(
                null,
                PaymentActionType.IGNORE_DUPLICATE,
                e.getRequest().merchant(),
                e.getRequest().amount(),
                0, null, null
        );
        return ApiResponse.success(response);
    }
}