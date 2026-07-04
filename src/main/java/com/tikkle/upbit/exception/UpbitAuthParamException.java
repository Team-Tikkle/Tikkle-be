package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitAuthParamException extends CustomException {
    public UpbitAuthParamException() {
        super(ErrorCode.UPBIT_AUTH_PARAM_ERROR);
    }
}
