package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitDepositInquiryFailedException extends CustomException {
    public UpbitDepositInquiryFailedException() {
        super(ErrorCode.UPBIT_DEPOSIT_INQUIRY_FAILED);
    }
}