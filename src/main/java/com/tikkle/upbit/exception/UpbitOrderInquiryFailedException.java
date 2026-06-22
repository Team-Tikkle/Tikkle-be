package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitOrderInquiryFailedException extends CustomException {
    public UpbitOrderInquiryFailedException() {
        super(ErrorCode.UPBIT_ORDER_INQUIRY_FAILED);
    }
}