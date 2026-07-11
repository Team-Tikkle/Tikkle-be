package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class PhoneAlreadyRegisteredException extends CustomException {
    public PhoneAlreadyRegisteredException() {
        super(ErrorCode.PHONE_ALREADY_REGISTERED);
    }
}