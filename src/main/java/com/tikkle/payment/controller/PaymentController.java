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

/**
 * 결제 푸시 알림 스크래핑 관련 API 엔드포인트를 제공하는 컨트롤러입니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController implements PaymentSwagger {
    private final PaymentService paymentService;

    /**
     * 안드로이드 클라이언트가 스크래핑한 결제 푸시 알림 데이터를 수신합니다.
     *
     * @param request 결제 스크래핑 요청 데이터
     * @return 결제 처리 결과 응답
     */
    @Override
    @PostMapping
    public ApiResponse<?> receivePaymentScraping(@Valid @RequestBody PaymentScrapingRequest request) {
        log.info("[PaymentController] 결제 스크래핑 요청 수신 - transactionId: {}", request.transactionId());
        
        var response = paymentService.processPayment(request);
        
        return ApiResponse.success(response);
    }
}