package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UnknownPaymentStatusException extends CustomException {
    public UnknownPaymentStatusException() {
        super(ErrorCode.UNKNOWN_PAYMENT_STATUS);
    }
}