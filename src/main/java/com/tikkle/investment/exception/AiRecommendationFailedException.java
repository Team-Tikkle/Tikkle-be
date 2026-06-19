package com.tikkle.investment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class AiRecommendationFailedException extends CustomException {
    public AiRecommendationFailedException() {
        super(ErrorCode.AI_RECOMMENDATION_FAILED);
    }
}