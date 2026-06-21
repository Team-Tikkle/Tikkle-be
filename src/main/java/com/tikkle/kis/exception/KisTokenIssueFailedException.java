package com.tikkle.kis.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class KisTokenIssueFailedException extends CustomException {
    public KisTokenIssueFailedException() {
        super(ErrorCode.KIS_TOKEN_ISSUE_FAILED);
    }
}