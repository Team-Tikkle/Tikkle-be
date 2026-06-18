package com.tikkle.user.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class LinkedAccountNotFoundException extends CustomException {
    public LinkedAccountNotFoundException() {
        super(ErrorCode.LINKED_ACCOUNT_NOT_FOUND);
    }
}