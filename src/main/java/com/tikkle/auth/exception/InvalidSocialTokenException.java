package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class InvalidSocialTokenException extends CustomException {
    public InvalidSocialTokenException() {
        super(ErrorCode.INVALID_SOCIAL_TOKEN);
    }
}