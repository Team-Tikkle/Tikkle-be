package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class ExpiredSignupTokenException extends CustomException {
    public ExpiredSignupTokenException() {
        super(ErrorCode.EXPIRED_SIGNUP_TOKEN);
    }
}