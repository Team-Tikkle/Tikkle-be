package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class DuplicatePaymentException extends CustomException {
    public DuplicatePaymentException() {
        super(ErrorCode.DUPLICATE_PAYMENT);
    }
}