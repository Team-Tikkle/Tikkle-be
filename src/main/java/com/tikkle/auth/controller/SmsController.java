package com.tikkle.auth.controller;

import com.tikkle.auth.dto.request.SmsSendRequest;
import com.tikkle.auth.dto.request.SmsVerifyRequest;
import com.tikkle.auth.dto.response.SmsVerifyResponse;
import com.tikkle.auth.swagger.SmsSwagger;
import com.tikkle.auth.service.SmsService;
import com.tikkle.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SMS Auth", description = "휴대폰 본인인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/sms")
public class SmsController implements SmsSwagger {

    private final SmsService smsService;

    @Override
    @PostMapping("/send")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SmsSendRequest request) {
        smsService.sendVerificationCode(request.getPhoneNumber());
        return ApiResponse.successWithNoData();
    }

    @Override
    @PostMapping("/verify")
    public ApiResponse<SmsVerifyResponse> verifyCode(@Valid @RequestBody SmsVerifyRequest request) {
        String token = smsService.verifyCodeAndGetToken(request.getPhoneNumber(), request.getCode());
        return ApiResponse.success(new SmsVerifyResponse(token));
    }
}
