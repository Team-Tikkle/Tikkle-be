package com.tikkle.auth.controller;

import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.service.TestTokenService;
import com.tikkle.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발 환경에서 테스트용 JWT 토큰을 손쉽게 발급받기 위한 컨트롤러입니다.
 * 이 컨트롤러는 "local" 프로필에서만 활성화되며, 운영 명세와 섞이지 않도록 Swagger 문서에서 제외합니다.
 */
@Hidden
@RestController
@Profile("local")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TestTokenController {
    private final TestTokenService testTokenService;

    /**
     * 특정 전화번호로 가입된 기존 유저의 토큰을 강제 발급합니다.
     *
     * @param phoneNumber 발급받을 사용자의 전화번호
     * @return 테스트용 JWT 토큰 쌍
     */
    @PostMapping("/test-token")
    public ApiResponse<TokenResponse> testToken(@RequestParam String phoneNumber) {
        return ApiResponse.success(testTokenService.generateTestToken(phoneNumber));
    }

    /**
     * 임의의 전화번호와 이름으로 신규 회원을 가입시키고 그 유저의 토큰을 발급합니다.
     *
     * @param phoneNumber 가입할 전화번호
     * @param name 가입할 이름
     * @return 테스트용 JWT 토큰 쌍
     */
    @PostMapping("/test-signup")
    public ApiResponse<TokenResponse> testSignup(@RequestParam String phoneNumber, @RequestParam String name) {
        return ApiResponse.success(testTokenService.generateTestSignupAndToken(phoneNumber, name));
    }
}