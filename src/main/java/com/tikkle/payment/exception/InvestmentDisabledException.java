package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvestmentDisabledException extends CustomException {
    public InvestmentDisabledException() {
        super(ErrorCode.INVESTMENT_DISABLED);
    }
}