package com.tikkle.kis.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class KisOrderFailedException extends CustomException {
    public KisOrderFailedException() {
        super(ErrorCode.KIS_ORDER_FAILED);
    }
}