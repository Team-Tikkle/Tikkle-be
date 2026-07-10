package com.tikkle.investment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvestmentProfileNotFoundException extends CustomException {
    public InvestmentProfileNotFoundException() {
        super(ErrorCode.INVESTMENT_PROFILE_NOT_FOUND);
    }
}