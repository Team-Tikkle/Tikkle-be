package com.tikkle.user.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvalidTwoFactorProviderException extends CustomException {
    public InvalidTwoFactorProviderException() {
        super(ErrorCode.INVALID_TWO_FACTOR_PROVIDER);
    }
}