package com.tikkle.auth.swagger;

import com.tikkle.auth.dto.request.GoogleLoginRequest;
import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.global.response.ApiResponse;
import com.tikkle.auth.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Auth", description = "인증 API")
public interface AuthSwagger {
    @Operation(summary = "구글 소셜 로그인", description = "프론트에서 발급받은 구글 액세스 토큰으로 로그인/회원가입 후 자체 JWT를 발급합니다. 응답의 isNewUser로 이번 로그인에서 신규 가입되었는지 구분할 수 있습니다.")
    ApiResponse<TokenResponse> googleLogin(@RequestBody @Valid GoogleLoginRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "로그아웃", description = "Refresh Token을 삭제하여 로그아웃합니다.")
    ApiResponse<?> logout(@Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다.")
    ApiResponse<TokenResponse> reissue(@RequestBody @Valid ReissueRequest request);
}