package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class ExpiredTimestampException extends CustomException {
    public ExpiredTimestampException() {
        super(ErrorCode.EXPIRED_TIMESTAMP);
    }
}