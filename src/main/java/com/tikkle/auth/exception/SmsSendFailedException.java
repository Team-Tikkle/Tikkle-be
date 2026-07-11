package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class SmsSendFailedException extends CustomException {
    public SmsSendFailedException() {
        super(ErrorCode.SMS_SEND_FAILED);
    }
}