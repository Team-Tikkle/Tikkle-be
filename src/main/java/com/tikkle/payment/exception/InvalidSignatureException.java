package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvalidSignatureException extends CustomException {
    public InvalidSignatureException() {
        super(ErrorCode.INVALID_SIGNATURE);
    }
}