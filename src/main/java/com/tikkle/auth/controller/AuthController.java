package com.tikkle.auth.controller;

import com.tikkle.auth.dto.request.GoogleLoginRequest;
import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.service.AuthService;
import com.tikkle.auth.service.OAuthService;
import com.tikkle.auth.swagger.AuthSwagger;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthSwagger {
    private final AuthService authService;
    private final OAuthService oAuthService;

    @Override
    @PostMapping("/oauth/google")
    public ApiResponse<TokenResponse> googleLogin(@RequestBody @Valid GoogleLoginRequest request) {
        return ApiResponse.success(oAuthService.googleLogin(request));
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