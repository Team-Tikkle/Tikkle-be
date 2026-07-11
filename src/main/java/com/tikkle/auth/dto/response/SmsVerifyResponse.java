package com.tikkle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SMS 검증 응답")
public record SmsVerifyResponse(
        @Schema(description = "가입용 임시 토큰")
        String signupToken
) {}
