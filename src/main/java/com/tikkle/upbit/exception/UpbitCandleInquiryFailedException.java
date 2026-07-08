package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitCandleInquiryFailedException extends CustomException {
    public UpbitCandleInquiryFailedException() {
        super(ErrorCode.UPBIT_CANDLE_INQUIRY_FAILED);
    }
}