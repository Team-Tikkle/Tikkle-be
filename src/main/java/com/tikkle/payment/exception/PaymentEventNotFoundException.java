package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class PaymentEventNotFoundException extends CustomException {
    public PaymentEventNotFoundException() {
        super(ErrorCode.PAYMENT_EVENT_NOT_FOUND);
    }
}