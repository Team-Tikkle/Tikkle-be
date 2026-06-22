package com.tikkle.upbit.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UpbitTokenIssueFailedException extends CustomException {
    public UpbitTokenIssueFailedException() {
        super(ErrorCode.UPBIT_TOKEN_ISSUE_FAILED);
    }
}