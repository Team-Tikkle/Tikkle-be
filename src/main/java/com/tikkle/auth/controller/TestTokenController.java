package com.tikkle.auth.controller;

import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.service.TestTokenService;
import com.tikkle.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TestTokenController {
    private final TestTokenService testTokenService;

    @PostMapping("/test-token")
    public ApiResponse<TokenResponse> testToken(@RequestParam String email) {
        return ApiResponse.success(testTokenService.generateTestToken(email));
    }

    @PostMapping("/test-signup")
    public ApiResponse<TokenResponse> testSignup(@RequestParam String email, @RequestParam String name) {
        return ApiResponse.success(testTokenService.generateTestSignupAndToken(email, name));
    }
}