package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class VerificationAttemptExceededException extends CustomException {
    public VerificationAttemptExceededException() {
        super(ErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
    }
}
