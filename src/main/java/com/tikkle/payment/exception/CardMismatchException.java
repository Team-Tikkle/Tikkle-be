package com.tikkle.payment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class CardMismatchException extends CustomException {
    public CardMismatchException() {
        super(ErrorCode.CARD_MISMATCH);
    }
}