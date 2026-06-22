package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitOrderExecutionFailedException extends CustomException {
    public UpbitOrderExecutionFailedException() {
        super(ErrorCode.UPBIT_ORDER_EXECUTION_FAILED);
    }
}