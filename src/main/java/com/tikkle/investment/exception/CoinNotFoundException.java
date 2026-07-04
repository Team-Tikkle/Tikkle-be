package com.tikkle.investment.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class CoinNotFoundException extends CustomException {
    public CoinNotFoundException() {
        super(ErrorCode.COIN_NOT_FOUND);
    }
}