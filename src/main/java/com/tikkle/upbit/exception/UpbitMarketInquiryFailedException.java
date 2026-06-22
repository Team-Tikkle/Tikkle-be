package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitMarketInquiryFailedException extends CustomException {
    public UpbitMarketInquiryFailedException() {
        super(ErrorCode.UPBIT_MARKET_INQUIRY_FAILED);
    }
}