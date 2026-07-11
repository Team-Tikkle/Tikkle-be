package com.tikkle.auth.swagger;

import com.tikkle.auth.dto.request.LoginRequest;
import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.request.SignupRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Auth", description = "회원가입/로그인 및 토큰 관리 API")
public interface AuthSwagger {
    @Operation(summary = "회원가입", description = "휴대폰 인증 완료 후 계정을 생성합니다.")
    ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request);

    @Operation(summary = "로그인", description = "휴대폰 번호와 비밀번호로 로그인합니다.")
    ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "로그아웃", description = "리프레시 토큰을 삭제하여 로그아웃합니다.")
    ApiResponse<Void> logout(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "토큰 재발급", description = "유효한 리프레시 토큰으로 액세스 토큰과 리프레시 토큰을 재발급합니다.")
    ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request);
}