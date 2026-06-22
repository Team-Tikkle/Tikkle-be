package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitTickerInquiryFailedException extends CustomException {
    public UpbitTickerInquiryFailedException() {
        super(ErrorCode.UPBIT_TICKER_INQUIRY_FAILED);
    }
}