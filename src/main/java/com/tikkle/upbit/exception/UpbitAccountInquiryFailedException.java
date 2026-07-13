package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitAccountInquiryFailedException extends CustomException {
    public UpbitAccountInquiryFailedException() {
        super(ErrorCode.UPBIT_ACCOUNT_INQUIRY_FAILED);
    }
}