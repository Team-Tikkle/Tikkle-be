package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitOrderCancelFailedException extends CustomException {
    public UpbitOrderCancelFailedException(String message) {
        super(ErrorCode.UPBIT_ORDER_CANCEL_FAILED, message);
    }
}