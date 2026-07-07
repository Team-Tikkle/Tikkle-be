package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class PaymentFilterConfigurationException extends CustomException {
    public PaymentFilterConfigurationException() {
        super(ErrorCode.PAYMENT_FILTER_CONFIGURATION_ERROR);
    }
}