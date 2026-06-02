package com.tikkle.user.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class WithdrawnUserException extends CustomException {
    public WithdrawnUserException() {
        super(ErrorCode.WITHDRAWN_USER);
    }
}