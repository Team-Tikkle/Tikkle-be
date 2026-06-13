package com.tikkle.onboarding.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class DuplicateCategoryRuleException extends CustomException {
    public DuplicateCategoryRuleException() {
        super(ErrorCode.DUPLICATE_CATEGORY_RULE);
    }
}