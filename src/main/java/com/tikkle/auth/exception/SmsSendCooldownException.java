package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class SmsSendCooldownException extends CustomException {
    public SmsSendCooldownException() {
        super(ErrorCode.SMS_SEND_COOLDOWN);
    }
}
