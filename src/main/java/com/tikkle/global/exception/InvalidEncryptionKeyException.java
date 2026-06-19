package com.tikkle.global.exception;

public class InvalidEncryptionKeyException extends CustomException {
    public InvalidEncryptionKeyException() {
        super(ErrorCode.INVALID_ENCRYPTION_KEY);
    }
}