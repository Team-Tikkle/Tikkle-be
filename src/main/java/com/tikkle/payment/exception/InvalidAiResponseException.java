package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvalidAiResponseException extends CustomException {
    public InvalidAiResponseException() {
        super(ErrorCode.INVALID_AI_RESPONSE);
    }
}