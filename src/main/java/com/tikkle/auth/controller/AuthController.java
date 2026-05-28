package com.tikkle.auth.controller;

import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.service.AuthService;
import com.tikkle.auth.swagger.AuthSwagger;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthSwagger {
    private final AuthService authService;

    // TODO: 소셜 로그인 구현 후 삭제
    @PostMapping("/test-token")
    public ApiResponse<TokenResponse> testToken(@RequestParam String email) {
        return ApiResponse.success(authService.generateTestToken(email));
    }

    @Override
    @PostMapping("/logout")
    public ApiResponse<?> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ApiResponse.successWithNoData();
    }

    @Override
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody @Valid ReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }
}