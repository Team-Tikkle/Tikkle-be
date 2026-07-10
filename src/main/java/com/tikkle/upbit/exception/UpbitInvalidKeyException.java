package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitInvalidKeyException extends CustomException {
    public UpbitInvalidKeyException() {
        super(ErrorCode.UPBIT_INVALID_KEY);
    }

    public UpbitInvalidKeyException(String message) {
        super(ErrorCode.UPBIT_INVALID_KEY, message);
    }
}