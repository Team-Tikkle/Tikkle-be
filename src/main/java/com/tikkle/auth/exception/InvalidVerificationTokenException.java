package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvalidVerificationTokenException extends CustomException {
    public InvalidVerificationTokenException() {
        super(ErrorCode.INVALID_VERIFICATION_TOKEN);
    }
}