package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitOrderFailedException extends CustomException {
    public UpbitOrderFailedException() {
        super(ErrorCode.UPBIT_ORDER_FAILED);
    }
}