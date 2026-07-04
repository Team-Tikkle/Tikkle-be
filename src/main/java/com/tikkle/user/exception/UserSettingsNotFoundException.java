package com.tikkle.user.exception;

import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;

public class UserSettingsNotFoundException extends CustomException {
    public UserSettingsNotFoundException() {
        super(ErrorCode.USER_SETTINGS_NOT_FOUND);
    }
}
