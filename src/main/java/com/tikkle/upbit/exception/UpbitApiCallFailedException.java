package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitApiCallFailedException extends CustomException {
    public UpbitApiCallFailedException() {
        super(ErrorCode.UPBIT_API_CALL_FAILED);
    }
}