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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트엔드로부터 인증 관련 요청(로그인, 로그아웃, 재발급)을 처리하는 컨트롤러입니다.
 * 인터페이스인 AuthSwagger를 구현하여 Swagger 관련 어노테이션을 분리했습니다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthSwagger {
    private final AuthService authService;
    private final OAuthService oAuthService;

    /**
     * 구글 소셜 로그인을 처리합니다.
     *
     * @param request 구글 액세스 토큰이 포함된 로그인 요청
     * @return 발급된 자사 JWT 토큰 및 신규 가입 여부
     */
    @Override
    @PostMapping("/oauth/google")
    public ApiResponse<TokenResponse> googleLogin(@RequestBody @Valid GoogleLoginRequest request) {
        return ApiResponse.success(oAuthService.googleLogin(request));
    }

    /**
     * 로그아웃을 처리합니다. 사용자의 리프레시 토큰을 레디스에서 삭제합니다.
     *
     * @param userDetails 현재 인증된 사용자 정보
     * @return 성공 응답
     */
    @Override
    @PostMapping("/logout")
    public ApiResponse<?> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ApiResponse.successWithNoData();
    }

    /**
     * 만료된 액세스 토큰을 대체하기 위해 리프레시 토큰을 사용하여 새로운 토큰 쌍을 발급합니다.
     *
     * @param request 기존 리프레시 토큰이 포함된 재발급 요청
     * @return 새로 발급된 토큰 쌍
     */
    @Override
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody @Valid ReissueRequest request) {
        return ApiResponse.success(authService.reissue(request));
    }
}