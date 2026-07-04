package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import lombok.Getter;

@Getter
public class DuplicatePaymentException extends CustomException {
    private final PaymentScrapingRequest request;

    public DuplicatePaymentException(PaymentScrapingRequest request) {
        super(ErrorCode.DUPLICATE_PAYMENT);
        this.request = request;
    }
}