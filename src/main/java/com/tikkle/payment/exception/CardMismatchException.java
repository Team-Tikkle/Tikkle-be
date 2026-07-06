package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import lombok.Getter;

public class CardMismatchException extends CustomException {
    public CardMismatchException() {
        super(ErrorCode.CARD_MISMATCH);
    }
}
