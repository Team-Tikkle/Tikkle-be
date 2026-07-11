package com.tikkle.auth.swagger;

import com.tikkle.auth.dto.request.SmsSendRequest;
import com.tikkle.auth.dto.request.SmsVerifyRequest;
import com.tikkle.auth.dto.response.SmsVerifyResponse;
import com.tikkle.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "SMS Auth", description = "휴대폰 본인인증 API")
public interface SmsSwagger {
    @Operation(summary = "인증번호 발송", description = "입력한 휴대폰 번호로 6자리 인증번호를 발송합니다.")
    ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SmsSendRequest request);

    @Operation(summary = "인증번호 검증", description = "발송된 인증번호를 확인하고, 회원가입 시 필요한 signupToken을 발급합니다.")
    ApiResponse<SmsVerifyResponse> verifyCode(@Valid @RequestBody SmsVerifyRequest request);
}
