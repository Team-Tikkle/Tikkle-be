package com.tikkle.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SmsVerifyResponse {
    private String signupToken;
}
