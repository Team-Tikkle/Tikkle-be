package com.tikkle.investment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class CoinSyncFailedException extends CustomException {
    public CoinSyncFailedException() {
        super(ErrorCode.COIN_SYNC_FAILED);
    }
}