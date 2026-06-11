package com.tikkle.payment.controller;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import com.tikkle.payment.service.PaymentService;
import com.tikkle.payment.swagger.PaymentSwagger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController implements PaymentSwagger {
    private final PaymentService paymentService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<?> receivePaymentScraping(@Valid @RequestBody PaymentScrapingRequest request) {
        // TODO: 추후 삭제 예정
        log.info("Received payment scraping request: {}", request);
        
        paymentService.processPaymentScraping(request);
        
        return ApiResponse.successWithNoData();
    }
}