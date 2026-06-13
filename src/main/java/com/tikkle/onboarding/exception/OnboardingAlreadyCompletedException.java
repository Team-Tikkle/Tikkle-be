package com.tikkle.onboarding.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class OnboardingAlreadyCompletedException extends CustomException {
    public OnboardingAlreadyCompletedException() {
        super(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
    }
}