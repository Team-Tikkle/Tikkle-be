package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitDepositFailedException extends CustomException {
    public UpbitDepositFailedException() {
        super(ErrorCode.UPBIT_DEPOSIT_FAILED);
    }
}