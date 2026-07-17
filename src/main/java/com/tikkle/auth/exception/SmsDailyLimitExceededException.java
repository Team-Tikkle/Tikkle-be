package com.tikkle.auth.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class SmsDailyLimitExceededException extends CustomException {
    public SmsDailyLimitExceededException() {
        super(ErrorCode.SMS_DAILY_LIMIT_EXCEEDED);
    }
}
