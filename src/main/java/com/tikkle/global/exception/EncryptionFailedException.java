package com.tikkle.global.exception;

public class EncryptionFailedException extends CustomException {
    public EncryptionFailedException() {
        super(ErrorCode.ENCRYPTION_FAILED);
    }
}