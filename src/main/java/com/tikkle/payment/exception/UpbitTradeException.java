package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitTradeException extends CustomException {
    public UpbitTradeException() {
        super(ErrorCode.UPBIT_TRADE_FAILED);
    }
}