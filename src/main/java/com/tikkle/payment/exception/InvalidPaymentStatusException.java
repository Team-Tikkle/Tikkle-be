package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvalidPaymentStatusException extends CustomException {
    public InvalidPaymentStatusException() {
        super(ErrorCode.INVALID_PAYMENT_STATUS);
    }
}