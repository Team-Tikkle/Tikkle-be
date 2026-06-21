package com.tikkle.global.exception;

public class InvalidInputValueException extends CustomException {
    public InvalidInputValueException() {
        super(ErrorCode.INVALID_INPUT_VALUE);
    }
}