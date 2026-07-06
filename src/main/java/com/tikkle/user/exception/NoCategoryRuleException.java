package com.tikkle.user.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class NoCategoryRuleException extends CustomException {
    public NoCategoryRuleException() {
        super(ErrorCode.NO_CATEGORY_RULE);
    }
}
